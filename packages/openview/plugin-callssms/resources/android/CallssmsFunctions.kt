package com.openview.plugins.callssms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeError
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse

/**
 * Bridge functions for the CallsSms NativePHP plugin on Android.
 *
 * Provides native phone dialler, SMS composition, WhatsApp integration,
 * and a contact picker that dispatches the ContactSelected event back to PHP.
 */
object CallsSmsFunctions {

    // ─────────────────────────────────────────────────────────────────────────
    // CallsSms.OpenPhoneDialer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open the device's native phone dialler pre-filled with [phone].
     *
     * Parameters:
     *   phone     – phone number string (any format accepted by the dialler)
     *   autoCall  – Boolean; true attempts an immediate call (ACTION_CALL),
     *               false (default) opens the dialler without calling (ACTION_DIAL).
     *
     * If autoCall = true but CALL_PHONE permission has not been granted the
     * coordinator requests the permission; on grant it places the call, on denial
     * it falls back to ACTION_DIAL so the user can still initiate manually.
     */
    class OpenPhoneDialer(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val phone = parameters["phone"] as? String
                ?: return BridgeResponse.error(BridgeError.InvalidParameters("phone parameter is required"))
            val autoCall = parameters["autoCall"] as? Boolean ?: false

            Handler(Looper.getMainLooper()).post {
                try {
                    if (autoCall) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            activity, Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            activity.startActivity(
                                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                            )
                        } else {
                            // Request permission; coordinator falls back to ACTION_DIAL on denial
                            val coordinator = CallsSmsCoordinator.install(activity)
                            coordinator.requestCallPermissionAndDial(phone)
                        }
                    } else {
                        activity.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OpenPhoneDialer error: ${e.message}", e)
                }
            }

            return BridgeResponse.success(mapOf("success" to true))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CallsSms.OpenSms
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open the device's native SMS application pre-filled with [phone] and
     * an optional [message] body.  No runtime permission is needed.
     *
     * Parameters:
     *   phone   – recipient phone number
     *   message – (optional) pre-filled SMS body
     */
    class OpenSms(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val phone = parameters["phone"] as? String
                ?: return BridgeResponse.error(BridgeError.InvalidParameters("phone parameter is required"))
            val message = parameters["message"] as? String ?: ""

            Handler(Looper.getMainLooper()).post {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                        putExtra("sms_body", message)
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "OpenSms error: ${e.message}", e)
                    // Fallback: try plain sms: URI
                    try {
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone")).apply {
                            putExtra("sms_body", message)
                        }
                        activity.startActivity(fallback)
                    } catch (fe: Exception) {
                        Log.e(TAG, "OpenSms fallback error: ${fe.message}", fe)
                    }
                }
            }

            return BridgeResponse.success(mapOf("success" to true))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CallsSms.OpenWhatsapp
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open WhatsApp for the given [phone] in either 'message' or 'call' mode.
     *
     * Parameters:
     *   phone   – phone number; international format recommended ("+15551234567")
     *   message – pre-filled text (only used when mode = 'message')
     *   mode    – 'message' (default) | 'call'
     *
     * WhatsApp does not expose a documented deep-link for voice calls on Android.
     * In 'call' mode we open WhatsApp directly to the contact profile/conversation
     * so the user can tap the call button with a single tap.
     */
    class OpenWhatsapp(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val phone = (parameters["phone"] as? String)?.trim()
                ?: return BridgeResponse.error(BridgeError.InvalidParameters("phone parameter is required"))
            val message = parameters["message"] as? String ?: ""
            val mode    = parameters["mode"] as? String ?: "message"

            // Normalise: keep leading '+' and strip everything else that isn't a digit
            val cleanPhone = if (phone.startsWith("+")) {
                "+" + phone.drop(1).filter { it.isDigit() }
            } else {
                phone.filter { it.isDigit() }
            }

            Handler(Looper.getMainLooper()).post {
                try {
                    // Attempt to launch WhatsApp directly.
                    // Using getPackageInfo() to detect WhatsApp fails silently on Android 11+
                    // due to package visibility restrictions, so we try the intent and catch
                    // ActivityNotFoundException instead.
                    when (mode) {
                        "call" -> openWhatsappCallIntent(cleanPhone)
                        else   -> openWhatsappMessageIntent(cleanPhone, message)
                    }
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.d(TAG, "WhatsApp not installed – falling back to wa.me browser")
                    try {
                        val encoded = java.net.URLEncoder.encode(message, "UTF-8")
                        val stripped = cleanPhone.trimStart('+')
                        val url = if (message.isEmpty()) "https://wa.me/$stripped"
                                  else "https://wa.me/$stripped?text=$encoded"
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (fe: Exception) {
                        Log.e(TAG, "WhatsApp browser fallback error: ${fe.message}", fe)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OpenWhatsapp error: ${e.message}", e)
                }
            }

            return BridgeResponse.success(mapOf("success" to true))
        }

        private fun openWhatsappMessageIntent(phone: String, message: String) {
            val encoded = java.net.URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("whatsapp://send?phone=$phone&text=$encoded")
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
            )
        }

        /**
         * WhatsApp does not publish an official call deep-link.
         * We open the WhatsApp conversation so the user needs only one tap to call.
         */
        private fun openWhatsappCallIntent(phone: String) {
            val uri = Uri.parse("whatsapp://send?phone=$phone")
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CallsSms.PickContact
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open the contact picker and dispatch a ContactSelected event once the user
     * selects a contact.
     *
     * Parameters:
     *   source – 'device' (default) uses the system contact picker;
     *            'whatsapp' uses WhatsApp's built-in contact list (or falls back
     *            to the system picker when WhatsApp is not installed).
     *
     * The result is returned asynchronously via the
     * Openview\Callssms\Events\ContactSelected event dispatched to PHP.
     */
    class PickContact(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val source = parameters["source"] as? String ?: "device"

            Handler(Looper.getMainLooper()).post {
                try {
                    val coordinator = CallsSmsCoordinator.install(activity)
                    coordinator.launchContactPicker(source)
                } catch (e: Exception) {
                    Log.e(TAG, "PickContact error: ${e.message}", e)
                }
            }

            return BridgeResponse.success(mapOf("success" to true, "message" to "Contact picker launched"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CallsSms.RequestPermissions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Request all permissions required by this plugin at once.
     *
     * Permissions requested: CALL_PHONE, READ_CONTACTS.
     *
     * If the user denies any permission a non-cancellable dialog is shown:
     *   • "Grant" re-opens the system permission prompt.
     *   • "Exit"  calls activity.finishAffinity() to close the app.
     *
     * Call this on app startup to ensure all features are available.
     */
    class RequestPermissions(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            Handler(Looper.getMainLooper()).post {
                try {
                    val coordinator = CallsSmsCoordinator.install(activity)
                    coordinator.requestAllPermissions()
                } catch (e: Exception) {
                    Log.e(TAG, "RequestPermissions error: ${e.message}", e)
                }
            }
            return BridgeResponse.success(mapOf("success" to true, "message" to "Permission request initiated"))
        }
    }

    private const val TAG = "CallsSmsFunctions"

    // Legacy stub – kept so any old bridge_functions declarations still resolve
    @Suppress("unused")
    class GetStatus(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.success(mapOf("status" to "ready", "version" to "1.0.0"))
        }
    }
}
