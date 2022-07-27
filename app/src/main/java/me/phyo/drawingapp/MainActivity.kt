package me.phyo.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception


class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var mCurrentColorImageButton : ImageButton? = null
    private var customDialog : Dialog? = null

    private var openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if (result.resultCode== RESULT_OK && result.data!=null){
                val backGroundImage : ImageView = findViewById(R.id.iv_background)
                backGroundImage.setImageURI(result.data?.data)
            }
        }

    private var requestPermission : ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        permissions ->
        permissions.entries.forEach{
            val permissionName = it.key
            val isGranted = it.value

            if (isGranted){
                Toast.makeText(this,"Permission Granted for Files",Toast.LENGTH_LONG).show()
                val pickIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }else{
                if (permissionName== Manifest.permission.READ_EXTERNAL_STORAGE){
                    Toast.makeText(this,"Permission Denied",Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)

        val galleryButton : ImageButton = findViewById(R.id.gallery_imgbtn)
        galleryButton.setOnClickListener {
            requestStoragePermission()
        }

        val undoButton : ImageButton = findViewById(R.id.undo_imgbtn)
        undoButton.setOnClickListener {
            drawingView?.undoDrawing()
        }

        val selectBrushBtn : ImageButton = findViewById(R.id.select_size_imgbtn)
        drawingView?.setSizeForBrush(10.toFloat())

        selectBrushBtn.setOnClickListener {
            showBrushSizeDialog()
        }

        val saveButton : ImageButton = findViewById(R.id.save_imgbtn)
        saveButton.setOnClickListener {
            showCustomProgressDialog()
            if (isReadingStorageAllowed()){
                lifecycleScope.launch {
                    val frameLayoutView : FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(frameLayoutView))
                }
            }

        }

    }

    private fun isReadingStorageAllowed():Boolean{
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,Manifest.permission.READ_EXTERNAL_STORAGE
        )
        ){
            showRationaleDialog("Kids Drawing App","Kids Drawing App"+"needs to access your external storage")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun showBrushSizeDialog(){
        var brushSizeDialog = Dialog(this)
        brushSizeDialog.setContentView(R.layout.dialog_brush_size)
        brushSizeDialog.setTitle("Brush Size : ")

        val smallBtn : ImageButton = brushSizeDialog.findViewById(R.id.small_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushSizeDialog.dismiss()
        }

        val mediumBtn: ImageButton = brushSizeDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushSizeDialog.dismiss()
        }

        val largeBtn: ImageButton = brushSizeDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(40.toFloat())
            brushSizeDialog.dismiss()
        }
        brushSizeDialog.show()
    }

    fun onClickedColors(view : View){
        if (view!==mCurrentColorImageButton){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()

            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,R.drawable.palette_pressed
                )
            )

            mCurrentColorImageButton?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,R.drawable.palette_normal
                )
            )
            mCurrentColorImageButton=view
        }
    }

    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view:View):Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val backgroundPhoto = view.background
        if (backgroundPhoto!=null){
            backgroundPhoto.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap:Bitmap?):String{
        var result=""
        withContext(Dispatchers.IO){
            if (mBitmap!=null){
                try {
                    val byte = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,byte)
                    val f = File(externalCacheDir?.absoluteFile.toString()+File.separator+"Drawing App"+ System.currentTimeMillis()/1000 + ".png")
                    val fo = FileOutputStream(f)
                    fo.write(byte.toByteArray())
                    fo.close()
                    result = f.absolutePath

                    runOnUiThread {
                        cancelCustomProgressDialog()
                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,"Successfully Saved to $result",Toast.LENGTH_SHORT).show()
                            shareFiles(result)
                        }else{
                            Toast.makeText(this@MainActivity,"Something went wrong.",Toast.LENGTH_SHORT).show()
                        }
                    }

                }
                catch (e:Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }


        return result
    }

    private fun showCustomProgressDialog(){
        customDialog = Dialog(this)
        customDialog?.setContentView(R.layout.custom_dialog)
        customDialog?.show()
    }
    private fun cancelCustomProgressDialog(){
        if (customDialog!=null){
            customDialog?.dismiss()
            customDialog=null
        }
    }
    private fun shareFiles(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path,uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"share"))
        }
    }
}