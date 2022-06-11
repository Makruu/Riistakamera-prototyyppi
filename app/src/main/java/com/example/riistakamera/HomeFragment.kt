package com.example.riistakamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.android.synthetic.main.fragment_home.*
import org.jetbrains.anko.doAsync
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.apache.commons.io.FileUtils
import java.lang.Math.abs
import kotlin.concurrent.*


class HomeFragment : Fragment() {

    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var safeContext: Context
    private lateinit var fileToUpload: File
    private lateinit var fileName: String
    private var fileNames: MutableList<String> = ArrayList()
    private var files: MutableList<String> = ArrayList()
    private lateinit var imgFile1: File
    private lateinit var imgFile2: File
    private var conBool: Boolean = false
    private var timBool: Boolean = false
    private var dirExistsBool: Boolean = false
    private var timer: CountDownTimer? = null
    private lateinit var baseUrl: String
    private var loopTracker: Int = 0
    private var loopTimer: Timer? = null
    private var timerDelay = 4000L // 4 sekunnin delay

    private var sardine: Sardine = OkHttpSardine()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        safeContext = context
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        //Kirjautumistiedot ownCloudia varten
        sardine.setCredentials(getString(R.string.username), getString(R.string.password))
        baseUrl = getString(R.string.URL)

        val globalclass = GlobalClass(requireContext().applicationContext)
        fileNames = globalclass.getList()
        files = globalclass.getFiles()
        /*fileNames.clear()
        files.clear()
        globalclass.setFiles(files)
        globalclass.setLists(fileNames)*/
        try {
            //Tarkistetaan saadaanko yhteys palvelimelle
            doAsync {
                if (sardine.exists(baseUrl)){
                    conBool = true
                    println("-------------------Sardine tapahtuu tässä-----------------------------")
                }
            }
            Handler(Looper.getMainLooper()).postDelayed(
                    {
                        if (fileNames.count() > 0) {
                            println("------------------------------Bufferi tapahtuu tässä----------------------------")
                            createDirectory()
                            startBuffertimer()
                        }
                    },
                    3000 // value in milliseconds
            )
        } catch (e: Exception) {
            println("-----------------------------------------------onViewCreated error catch-----------------------------------------------")
        }

        //Avaa linkin buttonista
        manButton.setOnClickListener {
            val url = "https://owncloud4.northeurope.cloudapp.azure.com"
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        }

        //Avaa asetukset
        setButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for take photo button
        camButton.setOnClickListener { timerToggle() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startTimer() {
        try {

            timer = object : CountDownTimer(60000, 6000) {
                override fun onTick(millisUntilFinished: Long) {
                    takePhoto()
                }
                override fun onFinish() {
                    //timer.start()
                    println("--------------------------------------startTimer() onFinish triggeröitynyt-----------------------------------------------")
                }
            }.start()
        } catch (e: Exception) {
            println("--------------------------------------Error startTimer():ssa-----------------------------------------------")
        }
    }

    //TODO: Korjaa kaatuminen timer.cancelin jälkeen
    private fun timerToggle() {
        try {
                doAsync {
                    if (sardine.exists(baseUrl)){
                        conBool = true
                    } else {
                        conBool = false
                    }
                }
            } catch (e: Exception) {
                println("Voi harmi")
            }
        try {
            if (!timBool) {
                camButton.setBackgroundColor(ContextCompat.getColor(safeContext, R.color.red))
                Toast.makeText(safeContext, "Riistakameran kuvaus aloitettu", Toast.LENGTH_SHORT).show()
                startTimer()
            }
            if (timBool) {
                camButton.setBackgroundColor(ContextCompat.getColor(safeContext, R.color.green))
                timer?.cancel()
                timer = null
                Toast.makeText(safeContext, "Riistakamera pysäytetty", Toast.LENGTH_SHORT).show()
            }
            timBool = !timBool
        } catch (e: Exception) {
            println("--------------------------------------Error timerToggle():ssa-----------------------------------------------")
        }
    }


    //Luo kansion ownCloudiin kameran nimellä
    private fun createDirectory() {
        val globalclass = GlobalClass(requireContext().applicationContext)
        val camName = globalclass.getCamName()
        try {
            if (!dirExistsBool) {
                doAsync{
                    if (!sardine.exists(baseUrl + camName)){
                        sardine.createDirectory(baseUrl + camName)
                        dirExistsBool = true
                    }
                }
            }
        } catch (e: Exception) {
            println("-------------------------------------------------------------------------------------")
            println(e)
            println("-------------------------------------------------------------------------------------")
        }
    }

    //Lataa otetun kuvan ownCloudiin
    private fun uploadFile() {
        val globalclass = GlobalClass(requireContext().applicationContext)
        val camName = globalclass.getCamName()
        try {
            var data: ByteArray = FileUtils.readFileToByteArray(File(fileToUpload.toString()))

            if (conBool) {
                doAsync {
                    sardine.put(baseUrl + camName + "/" + fileName, data)
                }
            } else {
                println("Tänne päädyttiin, ei ole yhteyttä")
                //Jos kuvien uploadaus epäonnistuu, lisätään ne bufferiin myöhempää varten
                fileNames.add(fileName)
                files.add(fileToUpload.toString())
                println("--------------------------------------------------" + fileNames.elementAt(0) + "--------------------------------------------------------")
                println("--------------------------------------------------" + files.elementAt(0) + "--------------------------------------------------------")
                globalclass.setLists(fileNames)
                globalclass.setFiles(files)
            }
        } catch (e: Exception) {
            println("-------------------------------------------------------------------------------------")
            println(e)
            println("-------------------------------------------------------------------------------------")
        }
    }

    //Puskuri kuvien uploadaamista varten, jos yhteys oli poikki
    private fun startBuffertimer() {
        loopTimer = Timer("Loop timer", false)

        loopTimer?.schedule(timerDelay, timerDelay) {
            loopAction()
        }
    }

    private fun loopAction() {
        val globalclass = GlobalClass(requireContext().applicationContext)
        val camName = globalclass.getCamName()
        var data: ByteArray

        if (conBool) {
            if (fileNames.count() > 0) {
                if (fileNames.count() > loopTracker) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(safeContext, "Ladataan kuvia, " + (fileNames.count() - loopTracker).toString() + " jäljellä, odota...", Toast.LENGTH_SHORT).show()
                    }
                    data = FileUtils.readFileToByteArray(File(files.elementAt(loopTracker)))
                    doAsync {
                        sardine.put(baseUrl + camName + "/" + fileNames.elementAt(loopTracker), data)
                    }
                    loopTracker++
                }
                if (fileNames.count() <= loopTracker) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(safeContext, "Kaikki kuvat puskettu!", Toast.LENGTH_SHORT).show()
                    }
                    loopTracker = 0
                    //Tyhjennetään puskuri
                    fileNames.clear()
                    files.clear()
                    globalclass.setFiles(files)
                    globalclass.setLists(fileNames)
                    cancelBuffertimer()
                    println("----------------------------------------Kuvat puskettu ja puskuri tyhjennetty----------------------------------------")
                }
            }
        }
    }

    private fun cancelBuffertimer() {
        loopTimer?.cancel()
        loopTimer?.purge()
        loopTimer = null
    }
    //-----------------------------------------------------------------------

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(safeContext)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }
            imageCapture = ImageCapture.Builder()
                    .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(safeContext))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        //Time-stamped file name for the photo
        fileName = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        // Create output file to hold the image
        val photoFile = File(
            outputDirectory,
            fileName
        )
        fileToUpload = photoFile
        println("-----------------------------------------------------------" + fileToUpload.toString() + "----------------------------------------")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(safeContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    //Toast.makeText(safeContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    createDirectory()
                    if(!::imgFile1.isInitialized)
                    {
                        imgFile1 = File(fileToUpload.toString())
                    }
                    else
                    {
                        imgFile2 = File(fileToUpload.toString())
                        val myBitmap1 = BitmapFactory.decodeFile(imgFile1.absolutePath)
                        val myBitmap2 = BitmapFactory.decodeFile(imgFile2.absolutePath)
                        detectMotion(myBitmap1, myBitmap2)
                        imgFile1 = File(fileToUpload.toString())
                    }
                }
            })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            safeContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val globalclass = GlobalClass(requireContext().applicationContext)
        val camName = globalclass.getCamName()
        val mediaDir = activity?.externalMediaDirs?.firstOrNull()?.let {
            File(it, "Riistakamera" + "/" + camName).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else activity?.filesDir!!
    }

    //Algoritmi kuvien liikkeen tunnistukseen
    // ---------------------------------------------------------------------------------------------
    private fun detectMotion(bitmap1: Bitmap, bitmap2: Bitmap) {
        val difference =
            getDifferencePercent(bitmap1.apply { scale(16, 12) }, bitmap2.apply { scale(16, 12) })
        if (difference > 10) { // customize accuracy
            // motion detected
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(safeContext, "Liikettä havaittu", Toast.LENGTH_SHORT).show()
            }
            uploadFile()
            println("----------------------------------------EROA LÖYDETTY-------------------------------------")
        } else {
            //Tänne tulee puhelimen kuvien poisto
            println("-------------------EE OO ERROO-------------------------------------")
        }
    }

    private fun getDifferencePercent(img1: Bitmap, img2: Bitmap): Double {
        if (img1.width != img2.width || img1.height != img2.height) {
            val f = "(%d,%d) vs. (%d,%d)".format(img1.width, img1.height, img2.width, img2.height)
            throw IllegalArgumentException("Images must have the same dimensions: $f")
        }
        var diff = 0L
        for (y in 0 until img1.height) {
            for (x in 0 until img1.width) {
                diff += pixelDiff(img1.getPixel(x, y), img2.getPixel(x, y))
            }
        }
        val maxDiff = 3L * 255 * img1.width * img1.height
        return 100.0 * diff / maxDiff
    }

    private fun pixelDiff(rgb1: Int, rgb2: Int): Int {
        val r1 = (rgb1 shr 16) and 0xff
        val g1 = (rgb1 shr 8) and 0xff
        val b1 = rgb1 and 0xff
        val r2 = (rgb2 shr 16) and 0xff
        val g2 = (rgb2 shr 8) and 0xff
        val b2 = rgb2 and 0xff
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
    }

    //----------------------------------------------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    safeContext,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}