package ax.nd.faceunlock

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ax.nd.faceunlock.pref.Prefs
import ax.nd.faceunlock.service.FaceAuthServiceCallbacks
import ax.nd.faceunlock.service.FaceAuthServiceController

class FaceAuthActivity : AppCompatActivity(), FaceAuthServiceCallbacks {
    private var controller: FaceAuthServiceController? = null
    private lateinit var prefs: Prefs
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_auth)
        prefs = FaceApplication.getApp().prefs
        controller = FaceAuthServiceController(this, prefs, this)
    }


    override fun onStart() {
        super.onStart()
        startTime = System.currentTimeMillis()
        controller?.start()
    }

    override fun onStop() {
        super.onStop()
        controller?.stop()
    }

    companion object {
        private const val TAG = "FaceAuthActivity"
    }

    override fun onAuthed() {
        val time = System.currentTimeMillis() - startTime
        // שימוש בסטרינג מהמשאבים עם פרמטר זמן
        Toast.makeText(
            this, 
            getString(R.string.auth_success, time), 
            Toast.LENGTH_SHORT
        ).show()
        
        // הוספת תוצאה חיובית לפני הסגירה כדי שה-MainActivity ידע שהאימות הצליח
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onError(errId: Int, message: String) {
        findViewById<TextView>(R.id.statusText).text = message
    }
}
