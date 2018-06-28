package mil.nga.giat.mage.test

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.FrameLayout
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment

class TestMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapFragment: SupportMapFragment
    var map: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = FrameLayout(this)
        layout.id = 0
        layout.contentDescription = "root"
        setContentView(layout)
        mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction().add(0, mapFragment, "map").commit()
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap?) {
        this.map = map
    }
}
