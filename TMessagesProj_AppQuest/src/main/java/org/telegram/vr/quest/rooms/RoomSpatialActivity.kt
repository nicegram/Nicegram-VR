package org.telegram.vr.quest.rooms

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

/** The room: the group's own Telegram chat on the large panel, presence and the native Telegram
 * call on the control panel beside it. No avatar synchronization, recording or media relay. */
class RoomSpatialActivity : AppSystemActivity() {
    private var room: RoomSession? = null
    private var calls: RoomCallBridge? = null
    private var panelView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var roomForeground = false
    private var roomFocused = true
    private var unfocusedAt = 0L
    private val shown = HashMap<Int, CharSequence>()
    private val tick = object : Runnable {
        override fun run() {
            val session = room ?: return
            if (session.closed || !session.validAccount()) { exitRoom(); return }
            if (!session.connected() || away()) calls?.pause()
            else if (roomForeground) calls?.resume()
            render()
            if (roomForeground) handler.postDelayed(this, 500)
        }
    }
    // Touching the chat panel moves window focus to that panel's activity. That is still the
    // room; only losing focus to anything else (system menu, another app) disarms the microphone.
    private val focusCheck = Runnable { if (away()) calls?.pause() }
    private fun focused() = roomFocused || RoomChatActivity.focused()
    private fun away() = !focused() && SystemClock.uptimeMillis() - unfocusedAt >= FOCUS_SETTLE_MS

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
        // One flat wall two metres ahead: the chat is the large surface, controls sit beside it.
        if (room != null) Entity.create(Transform(Pose(Vector3(CHAT_X, PANEL_Y, PANEL_Z))), Panel(R.id.room_chat_panel))
        Entity.create(Transform(Pose(Vector3(CONTROLS_X, PANEL_Y, PANEL_Z))), Panel(R.layout.vr_room_panel))
        Entity.create(
            Transform(Pose(Vector3(0f, -0.04f, 0f))), Scale(Vector3(8f, 0.05f, 8f)),
            Mesh(Uri.parse("mesh://box"), hittable = MeshCollision.NoCollision),
            Material().apply { baseColor = Color4(0.06f, 0.07f, 0.1f, 1f); unlit = true }
        )
    }
    override fun registerPanels(): List<PanelRegistration> {
        val panels = ArrayList<PanelRegistration>()
        val session = room
        if (session != null) panels.add(PanelRegistration(R.id.room_chat_panel) {
            panelIntent = RoomChatActivity.intent(this@RoomSpatialActivity, session)
            config {
                width = CHAT_WIDTH; height = PANEL_HEIGHT
                // About 0.6 px per millimetre: Telegram's 16 dp body text lands near one degree
                // of view at two metres, the comfortable reading size for a headset.
                layoutWidthInPx = 780; layoutHeightInPx = 870
                layerConfig = LayerConfig()
                enableTransparent = false
            }
        })
        panels.add(PanelRegistration(R.layout.vr_room_panel) {
            config {
                width = CONTROLS_WIDTH; height = PANEL_HEIGHT
                layoutDpi = 180; fractionOfScreen = 1f
                layerConfig = LayerConfig()
                enableTransparent = false
            }
            panel {
                panelView = rootView
                shown.clear()
                rootView?.findViewById<Button>(R.id.room_call)?.setOnClickListener { calls?.connect(); render() }
                rootView?.findViewById<Button>(R.id.room_mic)?.setOnClickListener { calls?.toggleMicrophone(); render() }
                rootView?.findViewById<Button>(R.id.room_exit)?.setOnClickListener { exitRoom() }
                render()
            }
        })
        return panels
    }
    private fun label(id: Int) = VrStrings.get(id)
    /** Writes a view only when its text changed: the panel is redrawn twice a second otherwise. */
    private fun put(root: View, id: Int, value: CharSequence) {
        if (shown[id]?.toString() == value.toString()) return
        shown[id] = value
        when (val view = root.findViewById<View>(id)) { is Button -> view.text = value; is TextView -> view.text = value }
    }
    private fun render() {
        val root = panelView ?: return
        val session = room ?: return
        put(root, R.id.room_title, label(R.string.vr_room_title))
        put(root, R.id.room_notice, label(R.string.vr_room_notice))
        put(root, R.id.room_status, when (session.state()) {
            RoomConnection.State.CONNECTED -> label(R.string.vr_room_connected)
            RoomConnection.State.RECONNECTING -> label(R.string.vr_room_disconnected)
            RoomConnection.State.ENDED -> label(RoomMessages.failure(session.endCode()))
        })
        val members = session.snapshot.optJSONArray("participants")
        put(root, R.id.room_people_title, String.format(label(R.string.vr_room_people_title), members?.length() ?: 0))
        put(root, R.id.room_people, (0 until (members?.length() ?: 0)).joinToString("\n") { "• " + members!!.getJSONObject(it).optString("name") })
        val service = RoomCallBridge.call(session)
        val people = calls?.people() ?: emptyList()
        put(root, R.id.room_call_title, if (service == null) label(R.string.vr_room_call_none) else String.format(label(R.string.vr_room_call_title), people.size))
        put(root, R.id.room_call_people, people.take(RoomCallBridge.LISTED).joinToString("\n") {
            (if (it.speaking) "🔊 " else if (it.muted) "🔇 " else "🎙 ") + it.name + if (it.self) label(R.string.vr_room_you) else ""
        } + if (people.size > RoomCallBridge.LISTED) "\n" + String.format(label(R.string.vr_room_more), people.size - RoomCallBridge.LISTED) else "")
        val status = calls?.status() ?: 0
        put(root, R.id.room_call_status, if (status == 0) "" else label(status))
        root.findViewById<Button>(R.id.room_call).apply {
            put(root, R.id.room_call, label(R.string.vr_room_call))
            visibility = if (service == null) View.VISIBLE else View.GONE
            isEnabled = session.connected() && calls?.busy() != true
        }
        root.findViewById<Button>(R.id.room_mic).apply {
            put(root, R.id.room_mic, label(if (service != null && !service.isMicMute) R.string.vr_room_mic_off else R.string.vr_room_mic_on))
            visibility = if (service != null) View.VISIBLE else View.GONE
            isEnabled = roomForeground && session.connected() && service?.callState == VoIPService.STATE_ESTABLISHED
        }
        put(root, R.id.room_exit, label(R.string.vr_room_exit))
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != RoomCallBridge.PERMISSION_REQUEST) return
        calls?.onPermissionResult(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        render()
    }
    override fun onResume() { super.onResume(); roomForeground = true; calls?.resume(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { roomForeground = false; handler.removeCallbacks(tick); handler.removeCallbacks(focusCheck); calls?.pause(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        roomFocused = hasFocus
        if (!hasFocus) unfocusedAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(focusCheck)
        if (hasFocus) { if (roomForeground) calls?.resume() } else handler.postDelayed(focusCheck, FOCUS_SETTLE_MS + 20)
    }
    @Deprecated("Android back compatibility")
    override fun onBackPressed() { exitRoom() }
    private fun exitRoom() {
        room?.let { RoomCallBridge.leaveCall(it); it.close() }
        RoomChatActivity.finishAll()
        val pending = PendingIntent.getActivity(this, 0, Intent(this, LaunchActivity::class.java).apply {
            action = Intent.ACTION_MAIN; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("extra_launch_in_home_pending_intent", pending))
        finish()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick); handler.removeCallbacks(focusCheck); calls?.dispose()
        room?.let { RoomCallBridge.leaveCall(it); it.close() }
        RoomChatActivity.finishAll()
        panelView = null
        super.onDestroy()
    }
    private companion object {
        const val PANEL_Y = 1.45f
        const val PANEL_Z = 2.0f
        const val PANEL_HEIGHT = 1.45f
        const val CHAT_WIDTH = 1.3f
        const val CONTROLS_WIDTH = 1.0f
        // Chat spans -0.25..1.05 m, controls -1.3..-0.3 m: a 5 cm gap, neither panel overlaps.
        const val CHAT_X = 0.4f
        const val CONTROLS_X = -0.8f
        const val FOCUS_SETTLE_MS = 300L
    }
}
