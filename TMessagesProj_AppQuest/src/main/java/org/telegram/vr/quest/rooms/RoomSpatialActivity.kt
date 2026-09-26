package org.telegram.vr.quest.rooms

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Color4
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.vr.VRFeature
import my.nicegram.vr.R
import org.telegram.messenger.voip.VoIPService
import org.telegram.ui.LaunchActivity
import org.telegram.vr.quest.VrStrings

/** First spatial slice: a shared presence panel and native Telegram call controls.
 * No avatar synchronization, chat mirroring, recording, or live-media relay. */
class RoomSpatialActivity : AppSystemActivity() {
    private var room: RoomSession? = null
    private var calls: RoomCallBridge? = null
    private var panelView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var roomForeground = false
    private var roomFocused = true
    private val tick = object : Runnable {
        override fun run() {
            val session = room ?: return
            if (session.closed || !session.validAccount()) { exitRoom(); return }
            if (!session.connected) calls?.pause()
            else if (roomForeground && roomFocused) calls?.resume()
            render()
            if (roomForeground) handler.postDelayed(this, 500)
        }
    }
    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))
    override fun onCreate(savedInstanceState: Bundle?) {
        room = RoomSession.active
        super.onCreate(savedInstanceState)
        val session = room
        if (session == null || session.closed || !session.validAccount()) { finish(); return }
        calls = RoomCallBridge(this, session)
    }
    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.setViewOrigin(0f, 0f, 0f, 0f)
        Entity.create(Transform(Pose(Vector3(0f, 1.5f, 2.0f))), Panel(R.layout.vr_room_panel))
        Entity.create(
            Transform(Pose(Vector3(0f, -0.04f, 0f))), Scale(Vector3(8f, 0.05f, 8f)),
            Mesh(Uri.parse("mesh://box"), hittable = MeshCollision.NoCollision),
            Material().apply { baseColor = Color4(0.06f, 0.07f, 0.1f, 1f); unlit = true }
        )
    }
    override fun registerPanels(): List<PanelRegistration> = listOf(
        PanelRegistration(R.layout.vr_room_panel) {
            config {
                width = 1.5f; height = 1.45f
                layoutDpi = 180; fractionOfScreen = 1f
                layerConfig = LayerConfig()
                enableTransparent = false
            }
            panel {
                panelView = rootView
                rootView?.findViewById<Button>(R.id.room_call)?.setOnClickListener { calls?.connect(); render() }
                rootView?.findViewById<Button>(R.id.room_mic)?.setOnClickListener { calls?.toggleMicrophone(); render() }
                rootView?.findViewById<Button>(R.id.room_exit)?.setOnClickListener { exitRoom() }
                render()
            }
        }
    )
    private fun label(id: Int) = VrStrings.get(id)
    private fun render() {
        val root = panelView ?: return
        val session = room ?: return
        root.findViewById<TextView>(R.id.room_title).text = label(R.string.vr_room_title)
        root.findViewById<TextView>(R.id.room_notice).text = label(R.string.vr_room_notice)
        root.findViewById<TextView>(R.id.room_status).text = label(if (session.connected) R.string.vr_room_connected else R.string.vr_room_disconnected)
        val members = session.snapshot.optJSONArray("participants")
        root.findViewById<TextView>(R.id.room_people).text = (0 until (members?.length() ?: 0)).joinToString("\n") { "• " + members!!.getJSONObject(it).optString("name") }
        val status = calls?.status() ?: 0
        root.findViewById<TextView>(R.id.room_call_status).text = if (status == 0) "" else label(status)
        root.findViewById<Button>(R.id.room_call).apply {
            text = label(R.string.vr_room_call)
            isEnabled = session.connected && RoomCallBridge.call(session) == null
        }
        val service = RoomCallBridge.call(session)
        root.findViewById<Button>(R.id.room_mic).apply {
            text = label(if (service != null && !service.isMicMute) R.string.vr_room_mic_off else R.string.vr_room_mic_on)
            isEnabled = roomForeground && session.connected && service?.callState == VoIPService.STATE_ESTABLISHED
        }
        root.findViewById<Button>(R.id.room_exit).text = label(R.string.vr_room_exit)
    }
    override fun onResume() { super.onResume(); calls?.resume(); roomForeground = true; handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { roomForeground = false; handler.removeCallbacks(tick); calls?.pause(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); roomFocused = hasFocus; if (!hasFocus) calls?.pause() else if (roomForeground) calls?.resume() }
    @Deprecated("Android back compatibility")
    override fun onBackPressed() { exitRoom() }
    private fun exitRoom() {
        room?.let { RoomCallBridge.leaveCall(it); it.close() }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, LaunchActivity::class.java).apply {
            action = Intent.ACTION_MAIN; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("extra_launch_in_home_pending_intent", pending))
        finish()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick); calls?.dispose()
        room?.let { RoomCallBridge.leaveCall(it); it.close() }
        panelView = null
        super.onDestroy()
    }
}
