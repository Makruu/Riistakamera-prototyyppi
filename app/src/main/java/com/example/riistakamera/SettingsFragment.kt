package com.example.riistakamera

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : Fragment() {

    private lateinit var safeContext: Context

    override fun onAttach(context: Context) {
        super.onAttach(context)
        safeContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val globalclass = GlobalClass(requireContext().applicationContext)
        val camName = globalclass.getCamName()

        editCamName.text = Editable.Factory.getInstance().newEditable(camName)

        //Tallentaa kameran nimen
        btnSave.setOnClickListener{
            globalclass.setCamName(editCamName.text.toString())
            hideKeyboard(safeContext, editCamName)
            Toast.makeText(safeContext, "Kameran nimi tallennettu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard(context: Context, v: View) {
        val iMm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        iMm.hideSoftInputFromWindow(v.windowToken, 0)
        v.clearFocus()
    }
}