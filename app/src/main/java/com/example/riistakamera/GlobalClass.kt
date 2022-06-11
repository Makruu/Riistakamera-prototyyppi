package com.example.riistakamera

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class GlobalClass(context: Context) {
    val preference_camera_name = "CameraName"

    val preference = context.getSharedPreferences("sharedPref", Context.MODE_PRIVATE)
    val preferences = context.getSharedPreferences("LIST", Context.MODE_PRIVATE)
    val preferencefile = context.getSharedPreferences("FILE", Context.MODE_PRIVATE)

    fun getCamName(): String? {
        return preference.getString(preference_camera_name, "")
    }

    fun setCamName(name: String) {
        val editor = preference.edit()
        editor.putString(preference_camera_name, name)
        editor.apply()
    }

    fun setLists(list: MutableList<String>){
        val editor = preferences.edit()
        val gson = Gson()
        val json = gson.toJson(list)//converting list to Json
        editor.putString("LIST",json)
        //val set: Set<String> = HashSet()
        editor.apply()
    }
    //getting the list from shared preference
    fun getList():MutableList<String>{
        val gson = Gson()
        val json = preferences.getString("LIST", null)
        val type = object : TypeToken<MutableList<String>>(){}.type//converting the json to list
        return gson.fromJson(json, type)//returning the list
    }

    fun setFiles(list: MutableList<String>){
        val editor = preferencefile.edit()
        val gson = Gson()
        val json = gson.toJson(list)//converting list to Json
        editor.putString("FILE", json)
        editor.apply()
    }

    fun getFiles():MutableList<String>{
        val gson = Gson()
        val json = preferencefile.getString("FILE", null)
        val type = object : TypeToken<MutableList<String>>(){}.type//converting the json to list
        return gson.fromJson(json, type)//returning the list
    }
}