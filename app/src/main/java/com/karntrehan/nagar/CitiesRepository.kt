package com.karntrehan.nagar

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.content.SharedPreferences
import android.util.Log
import com.karntrehan.nagar.data.CityDao
import com.karntrehan.nagar.data.entities.CitiesResponse
import com.karntrehan.nagar.data.entities.CityEntity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Created by karn on 14-08-2017.
 */
@Singleton
class CitiesRepository @Inject constructor(
        val cityDao: CityDao,
        val citiesService: CitiesService,
        val preferences: SharedPreferences
) : CitiesContract.Repository {

    val TAG = "CitiesRepo"

    private val offsetLive = MutableLiveData<Int>()
    private val loadStatusLive = MutableLiveData<Boolean>()

    val cities: LiveData<List<CityEntity>> = Transformations.switchMap(offsetLive)
    { offset ->
        cityDao.loadLocalCities(offset, Constants.LIMIT)
    }

    override fun getCities(offset: Int): LiveData<List<CityEntity>> {
        offsetLive.value = offset
        Thread(Runnable {

            val dbCount = cityDao.loadCitiesCount()
            Log.d(TAG, "DB: $dbCount")
            Log.d(TAG, "Prefs: ${preferences.getInt(Constants.MAX_REMOTE_CITIES_COUNT, Int.MAX_VALUE)}")

            if (dbCount < preferences.getInt(Constants.MAX_REMOTE_CITIES_COUNT, Int.MAX_VALUE)) {
                getRemoteCities(offset, Constants.LIMIT)
            } else if (dbCount <= offset) loadStatusLive.postValue(false)

        }).start()

        return cities
    }

    override fun getRemoteCities(offset: Int, limit: Int) {
        Log.d(TAG, "getRemoteCities $offset")

        val call: Call<CitiesResponse> = citiesService.getCities(limit, offset)
        call.enqueue(object : Callback<CitiesResponse> {
            override fun onResponse(call: Call<CitiesResponse>?, response: Response<CitiesResponse>?) {
                if (response!!.isSuccessful) {
                    val citiesResponse = response.body()
                    Log.d(TAG, "Success: " + citiesResponse.toString())

                    if (citiesResponse == null)
                        return

                    Thread(Runnable {
                        cityDao.insertAllCities(citiesResponse.cities)
                        preferences
                                .edit()
                                .putInt(Constants.MAX_REMOTE_CITIES_COUNT,
                                        citiesResponse.cMetaResponse.totalCount)
                                .apply()
                        //loadStatusLive.postValue(citiesResponse.cities.isEmpty())
                    }).start()
                }
            }

            override fun onFailure(call: Call<CitiesResponse>?, t: Throwable?) {
                Log.d(TAG, "Failure: " + t?.localizedMessage)
            }

        })
    }


    override fun deleteCities() {
        Thread(Runnable {
            cityDao.deleteAll()
        }).start()
    }

    override fun getLoadingStatus(position: Int?): LiveData<Boolean> {
        return loadStatusLive
    }

}