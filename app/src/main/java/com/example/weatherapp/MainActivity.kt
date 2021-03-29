package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressBarDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()

        // Checking if the user has location services turned on
        if (!isLocationEnabled()){
            Toast.makeText(this, "Your location services are off. Please turn it on", Toast.LENGTH_SHORT).show()
            // Redirecting the user to the Location Settings of the app
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            // Asking for permissions
            Dexter.withActivity(this).withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()){
                        requestLocationData()
                    }
                    if (report!!.isAnyPermissionPermanentlyDenied){
                        Toast.makeText(this@MainActivity,
                        "You have denied permissions. It os mandatory to make the app work", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                    showRationalePermissionsDialog()
                }
            }).onSameThread().check()
        }
    }

    // Location request data permission
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    // Getting the weather details
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        // Checking the internet connection
        if (Constants.isNetworkAvailable(this)){
            // Using 3rd party library to call API calls
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            // Connecting the retrofit service with the Weather Service interface for preparing to call
            val service: WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)
            // Creating a call
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )
            showCustomProgressDialog()
            // This process is in background
            listCall.enqueue(object : Callback<WeatherResponse>{
                // Any error or it fails to load
                override fun onFailure(t: Throwable?) {
                    Log.e("Error!!!", t!!.message.toString())
                    hideProgressDialog()
                }
                // For successful HTTP response
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if (response!!.isSuccess){
                        hideProgressDialog()
                        // body() means the whole JSON objects that we'll be getting from the web
                        val weatherList: WeatherResponse = response.body()
                        // Storing the weather response in Shared Preference
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("Response Result", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> { Log.e("Error 400", "Bad Connection") }
                            404 -> { Log.e("Error 404", "Not Found") }
                            else -> { Log.e("Error", "Generic Error")}
                        }
                    }
                }

            })
        }else{
            Toast.makeText(this@MainActivity, "No Internet connection available", Toast.LENGTH_SHORT).show()
        }
    }

    // Alert Dialog to aware the user that the permissions are denied
    private fun showRationalePermissionsDialog(){
        AlertDialog.Builder(this).setTitle("Permission Denied")
                .setMessage("It looks like you have turned off the permissions required for this feature. " +
                        "It can be enabled under Application Settings")
                .setPositiveButton("GO TO SETTINGS"){
                    _, _ ->
                    try {
                        // Opening the Settings for our particular app
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                    }
                }.setNegativeButton("CANCEL"){
                    dialog, _ -> dialog.cancel()
                }.show()
    }

    // This provides access to the system location services
    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Showing custom dialog
    private fun showCustomProgressDialog(){
        mProgressBarDialog = Dialog(this)
        mProgressBarDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressBarDialog!!.show()
    }

    // For the refresh action
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // What should happen when the refresh is clicked
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }else -> return super.onOptionsItemSelected(item)
        }
    }

    // Hiding the dialog
    private fun hideProgressDialog(){
        if (mProgressBarDialog != null){
            mProgressBarDialog!!.dismiss()
        }
    }

    // Connecting the UI with code
    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (i in weatherList.weather.indices){
                Log.i("Weather Name", weatherList.weather.toString())
                // Connecting the UI
                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = (weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString()))
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)
                tv_humidity.text = (weatherList.main.humidity.toString() + " per cent")
                tv_max.text = (weatherList.main.temp_max.toString() + " max")
                tv_min.text = (weatherList.main.temp_min.toString() + " min")
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                // Changing the icons accordingly
                when(weatherList.weather[i].icon){
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "09d" -> iv_main.setImageResource(R.drawable.rain)
                    "09n" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                    "50d" -> iv_main.setImageResource(R.drawable.mist)
                    "50n" -> iv_main.setImageResource(R.drawable.mist)
                }
            }
        }
    }

    // For the unit
    private fun getUnit(value: String): String?{
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            value = "F"
        }
        return value
    }

    // For getting the time to decide sunrise and sunset
    private fun unixTime(timex: Long): String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}