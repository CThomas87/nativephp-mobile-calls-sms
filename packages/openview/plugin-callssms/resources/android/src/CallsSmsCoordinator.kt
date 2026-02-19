package com.openview.plugins.callssms

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.utils.NativeActionCoordinator
import org.json.JSONObject

/**
 * CallsSmsCoordinator is a headless [Fragment] that manages interactions
 * requiring the Activity back-stack:
 *
 *  - Requesting the CALL_PHONE permission (for auto-call)
 *  - Launching the device or WhatsApp contact picker
 *  - Dispatching the ContactSelected event back to PHP
 *
 * Install it once per activity using [install].
 */
class CallsSmsCoordinator : Fragment() {

    // Phone number waiting for CALL_PHONE permission grant
    private var pendingCallPhone: String? = null

    // Contact source waiting for READ_CONTACTS permission grant
    private var pendingContactSource: String? = null

    // ── Permission launcher: CALL_PHONE ──────────────────────────────────────

    private val callPhoneLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val phone = pendingCallPhone ?: return@registerForActivityResult
            pendingCallPhone = null

            if (granted) {
                Log.d(TAG, "✅ CALL_PHONE grant – dialling $phone")
                try {
                    requireActivity().startActivity(
                        Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "ACTION_CALL failed: ${e.message}", e)
                    dialFallback(phone)
                }
            } else {
                Log.d(TAG, "❌ CALL_PHONE denied – opening dialler instead")
                dialFallback(phone)
            }
        }

    // ── Permission launcher: ALL required permissions (startup) ────────────────

    private val allPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                Log.d(TAG, "✅ All required permissions granted")
            } else {
                val denied = results.filterValues { !it }.keys
                    .map { it.substringAfterLast('.') }
                    .joinToString(", ")
                Log.d(TAG, "❌ Permissions denied: $denied – showing exit dialog")
                showPermissionRequiredDialog()
            }
        }

    private fun showPermissionRequiredDialog() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded) return@postDelayed
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Permissions Required")
                .setMessage(
                    "This app needs Phone and Contacts permissions to make calls, " +
                    "send messages, and pick contacts.\n\n" +
                    "Please grant both permissions to continue using the app."
                )
                .setPositiveButton("Grant Permissions") { dialog, _ ->
                    dialog.dismiss()
                    allPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
                }
                .setNegativeButton("Exit App") { _, _ ->
                    requireActivity().finishAffinity()
                }
                .setCancelable(false)
                .show()
        }, 300)
    }

    /**
     * Check and request all permissions required by this plugin.
     * If any are missing the system dialog is shown; on denial a non-cancellable
     * exit dialog is presented.
     */
    fun requestAllPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter { perm ->
            ContextCompat.checkSelfPermission(requireContext(), perm) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Log.d(TAG, "✅ All permissions already granted")
            return
        }
        Log.d(TAG, "Requesting missing permissions: ${missing.map { it.substringAfterLast('.') }}")
        allPermissionsLauncher.launch(missing.toTypedArray())
    }

    // ── Permission launcher: READ_CONTACTS ─────────────────────────────────────

    private val readContactsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val source = pendingContactSource ?: return@registerForActivityResult
            pendingContactSource = null

            if (granted) {
                Log.d(TAG, "✅ READ_CONTACTS granted – launching $source contact picker")
                doLaunchContactPicker(source)
            } else {
                Log.d(TAG, "❌ READ_CONTACTS denied")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        requireContext(),
                        "Contacts permission is required to pick a contact.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    // ── Activity-result launchers: contact pickers ───────────────────────────

    /** Device contact picker (opens system Contacts app, returns a Phone URI) */
    private val deviceContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> readPhoneUri(uri, source = "device") }
            }
        }

    /** WhatsApp contact picker (returns a WhatsApp profile URI) */
    private val whatsappContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    readWhatsappUri(uri)
                } else {
                    // No data returned – fall back to device picker
                    launchDeviceContactPicker()
                }
            }
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "CallsSmsCoordinator created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallsSmsCoordinator destroyed")
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Request CALL_PHONE permission if not already granted, then place the call.
     * Falls back to ACTION_DIAL on denial.
     */
    fun requestCallPermissionAndDial(phone: String) {
        pendingCallPhone = phone

        val already = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (already) {
            try {
                requireActivity().startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                )
            } catch (e: Exception) {
                dialFallback(phone)
            }
        } else {
            callPhoneLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    /**
     * Open the contact picker.
     *
     * @param source 'device' – system contact picker
     *               'whatsapp' – WhatsApp contact list (falls back to device
     *                            when WhatsApp is not installed)
     */
    fun launchContactPicker(source: String) {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.d(TAG, "READ_CONTACTS not granted – requesting")
            pendingContactSource = source
            readContactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            return
        }

        doLaunchContactPicker(source)
    }

    private fun doLaunchContactPicker(source: String) {
        when (source) {
            "whatsapp" -> {
                // On Android 11+ getPackageInfo() silently fails due to package visibility.
                // Instead attempt to launch the WhatsApp picker directly and catch any
                // ActivityNotFoundException, then fall back to device contacts.
                val intent = Intent(Intent.ACTION_PICK).apply {
                    type = "vnd.android.cursor.dir/vnd.com.whatsapp.profile"
                    setPackage("com.whatsapp")
                }
                val canResolve = requireContext().packageManager
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

                if (canResolve) {
                    launchWhatsappContactPicker()
                } else {
                    Toast.makeText(requireContext(), "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                    launchDeviceContactPicker()
                }
            }
            else -> launchDeviceContactPicker()
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun dialFallback(phone: String) {
        try {
            requireActivity().startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_DIAL fallback failed: ${e.message}", e)
        }
    }

    private fun launchDeviceContactPicker() {
        deviceContactLauncher.launch(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        )
    }

    private fun launchWhatsappContactPicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            // WhatsApp exposes this MIME type for its internal contact picker
            type = "vnd.android.cursor.dir/vnd.com.whatsapp.profile"
            setPackage("com.whatsapp")
        }
        whatsappContactLauncher.launch(intent)
    }

    /** Read name + phone from a ContactsContract.CommonDataKinds.Phone URI. */
    private fun readPhoneUri(uri: Uri, source: String) {
        try {
            val resolver = requireContext().contentResolver
            val cursor = resolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null, null
            ) ?: return

            cursor.use {
                if (it.moveToFirst()) {
                    val name  = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
                    val phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    dispatchContactSelected(name, phone, source)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readPhoneUri error: ${e.message}", e)
        }
    }

    /**
     * Read contact info from a WhatsApp profile URI.
     *
     * WhatsApp's content provider schema varies between versions; we try common
     * column names and fall back gracefully.
     *
     * Typical columns:  display_name,  jid  (format: <phone>@s.whatsapp.net)
     */
    private fun readWhatsappUri(uri: Uri) {
        try {
            val resolver = requireContext().contentResolver
            val cursor = resolver.query(uri, null, null, null, null)

            if (cursor == null) {
                launchDeviceContactPicker()
                return
            }

            cursor.use {
                if (it.moveToFirst()) {
                    val columns = it.columnNames.toList()
                    Log.d(TAG, "WhatsApp contact columns: $columns")

                    val nameIdx = columns.indexOfFirst { c -> c.contains("name", ignoreCase = true) }
                    val jidIdx  = columns.indexOf("jid")
                    val numIdx  = columns.indexOfFirst { c -> c.contains("number", ignoreCase = true) || c.contains("phone", ignoreCase = true) }

                    val name  = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val phone: String = when {
                        jidIdx >= 0 -> {
                            // jid format: +15551234567@s.whatsapp.net  or  15551234567@s.whatsapp.net
                            val jid = it.getString(jidIdx) ?: ""
                            val raw = jid.substringBefore("@")
                            if (raw.startsWith("+")) raw else if (raw.isNotEmpty()) "+$raw" else ""
                        }
                        numIdx >= 0 -> it.getString(numIdx) ?: ""
                        else -> ""
                    }

                    if (phone.isNotEmpty()) {
                        dispatchContactSelected(name, phone, source = "whatsapp")
                    } else {
                        Log.w(TAG, "Could not extract phone from WhatsApp URI – falling back")
                        launchDeviceContactPicker()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readWhatsappUri error: ${e.message}", e)
            // Degrade gracefully to device picker
            launchDeviceContactPicker()
        }
    }

    /** Dispatch the ContactSelected PHP event to Livewire via NativeActionCoordinator. */
    private fun dispatchContactSelected(name: String, phone: String, source: String) {
        val eventClass = "Openview\\Callssms\\Events\\ContactSelected"
        val payload = JSONObject().apply {
            put("name",   name)
            put("phone",  phone)
            put("source", source)
        }

        Log.d(TAG, "Dispatching $eventClass – name=$name, phone=$phone, source=$source")

        val activity = requireActivity()
        Handler(Looper.getMainLooper()).post {
            NativeActionCoordinator.dispatchEvent(activity, eventClass, payload.toString())
        }
    }

    companion object {
        private const val TAG = "CallsSmsCoordinator"

        /**
         * Find an existing [CallsSmsCoordinator] attached to [activity] or
         * create and attach a new one.  Thread-safe via main-thread commitNow.
         */
        fun install(activity: FragmentActivity): CallsSmsCoordinator =
            activity.supportFragmentManager
                .findFragmentByTag(TAG) as? CallsSmsCoordinator
                ?: CallsSmsCoordinator().also {
                    activity.supportFragmentManager
                        .beginTransaction()
                        .add(it, TAG)
                        .commitNow()
                }

        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
        )
    }
}
