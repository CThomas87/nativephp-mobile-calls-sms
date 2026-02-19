import Foundation
import UIKit
import MessageUI
import Contacts
import ContactsUI

// MARK: - CallsSms Function Namespace

/// Bridge functions for the CallsSms NativePHP plugin on iOS.
///
/// Provides native phone dialling, SMS composition (via MFMessageComposeViewController),
/// WhatsApp deep-link integration, and a CNContactPickerViewController-based contact
/// picker that feeds results back to PHP via LaravelBridge events.
enum CallsSmsFunctions {

    // MARK: - CallsSms.OpenPhoneDialer

    /// Open the native Phone app pre-filled with `phone`.
    ///
    /// Parameters:
    ///   - phone    – phone number string (any format accepted by iOS)
    ///   - autoCall – Bool; true uses `tel://` (immediate call), false uses
    ///                `telprompt://` (shows a confirmation dialog – better UX).
    ///
    /// Both schemes are blocked on devices that cannot place phone calls (e.g. iPad
    /// without cellular); the function returns silently in that case.
    class OpenPhoneDialer: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let phone = parameters["phone"] as? String, !phone.isEmpty else {
                return BridgeResponse.error(message: "phone parameter is required")
            }

            let autoCall = parameters["autoCall"] as? Bool ?? false
            // Percent-encode so any special characters don't break the URL
            let encoded  = phone.addingPercentEncoding(withAllowedCharacters: .urlHostAllowed) ?? phone
            let scheme   = autoCall ? "tel" : "telprompt"

            DispatchQueue.main.async {
                guard let url = URL(string: "\(scheme)://\(encoded)") else { return }

                if UIApplication.shared.canOpenURL(url) {
                    UIApplication.shared.open(url, options: [:], completionHandler: nil)
                } else if autoCall,
                          let fallback = URL(string: "telprompt://\(encoded)"),
                          UIApplication.shared.canOpenURL(fallback) {
                    // telprompt:// sometimes succeeds where tel:// doesn't
                    UIApplication.shared.open(fallback, options: [:], completionHandler: nil)
                }
            }

            return BridgeResponse.success(data: ["success": true])
        }
    }

    // MARK: - CallsSms.OpenSms

    /// Open the native Messages app with `phone` pre-filled as recipient and
    /// `message` as the body text.
    ///
    /// Uses MFMessageComposeViewController (full compose sheet) when available,
    /// otherwise falls back to the `sms:` URL scheme.
    ///
    /// Parameters:
    ///   - phone   – recipient phone number
    ///   - message – (optional) pre-filled message body
    class OpenSms: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let phone = parameters["phone"] as? String, !phone.isEmpty else {
                return BridgeResponse.error(message: "phone parameter is required")
            }

            let message = parameters["message"] as? String ?? ""

            DispatchQueue.main.async {
                if MFMessageComposeViewController.canSendText() {
                    CallsSmsMessageComposer.shared.present(recipient: phone, body: message)
                } else {
                    // Fallback: sms: URL scheme (body may not pre-fill on all iOS versions)
                    let encodedBody = message.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                    let raw = encodedBody.isEmpty ? "sms:\(phone)" : "sms:\(phone)&body=\(encodedBody)"
                    if let url = URL(string: raw) {
                        UIApplication.shared.open(url, options: [:], completionHandler: nil)
                    }
                }
            }

            return BridgeResponse.success(data: ["success": true])
        }
    }

    // MARK: - CallsSms.OpenWhatsapp

    /// Open WhatsApp in message or call mode for the given `phone`.
    ///
    /// Parameters:
    ///   - phone   – phone in international format ("+15551234567")
    ///   - message – pre-filled text; only used when mode = 'message'
    ///   - mode    – 'message' (default) | 'call'
    ///
    /// Call mode: tries the `whatsapp://call?phone=…` deep link (unofficial but
    /// widely supported as of WhatsApp ≥ 2.x).  Falls back to opening the chat.
    ///
    /// When WhatsApp is not installed the universal `https://wa.me/…` link opens
    /// in the browser and prompts the user to install WhatsApp.
    class OpenWhatsapp: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let phone = (parameters["phone"] as? String)?.trimmingCharacters(in: .whitespaces),
                  !phone.isEmpty else {
                return BridgeResponse.error(message: "phone parameter is required")
            }

            let message = parameters["message"] as? String ?? ""
            let mode    = parameters["mode"]    as? String ?? "message"

            // Normalise: keep leading "+" and strip non-digit characters
            let cleanPhone: String = {
                if phone.hasPrefix("+") {
                    return "+" + phone.dropFirst().filter { $0.isNumber }
                }
                return phone.filter { $0.isNumber }
            }()

            // wa.me requires digits only (no "+")
            let waPhone = cleanPhone.hasPrefix("+") ? String(cleanPhone.dropFirst()) : cleanPhone

            DispatchQueue.main.async {
                let isInstalled = UIApplication.shared.canOpenURL(URL(string: "whatsapp://")!)

                if mode == "call" && isInstalled {
                    // Try the WhatsApp call deep-link (unofficial; works on most current versions)
                    if let callURL = URL(string: "whatsapp://call?phone=\(cleanPhone)"),
                       UIApplication.shared.canOpenURL(callURL) {
                        UIApplication.shared.open(callURL, options: [:], completionHandler: nil)
                        return
                    }
                }

                // Build message / chat URL
                let encodedMsg = message.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                let urlString: String
                if isInstalled {
                    urlString = encodedMsg.isEmpty
                        ? "whatsapp://send?phone=\(cleanPhone)"
                        : "whatsapp://send?phone=\(cleanPhone)&text=\(encodedMsg)"
                } else {
                    urlString = encodedMsg.isEmpty
                        ? "https://wa.me/\(waPhone)"
                        : "https://wa.me/\(waPhone)?text=\(encodedMsg)"
                }

                if let url = URL(string: urlString) {
                    UIApplication.shared.open(url, options: [:], completionHandler: nil)
                }
            }

            return BridgeResponse.success(data: ["success": true])
        }
    }

    // MARK: - CallsSms.PickContact

    /// Present the system contact picker and dispatch a ContactSelected event
    /// with the chosen name + phone back to PHP.
    ///
    /// Parameters:
    ///   - source – 'device' (default) | 'whatsapp'
    ///              iOS does not expose a WhatsApp-specific contact picker API.
    ///              When source = 'whatsapp' the system contact picker is shown;
    ///              the selected number is then usable with openWhatsapp().
    class PickContact: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let source = parameters["source"] as? String ?? "device"

            DispatchQueue.main.async {
                CallsSmsContactPicker.shared.present(source: source)
            }

            return BridgeResponse.success(data: ["success": true, "message": "Contact picker launched"])
        }
    }

    // MARK: - CallsSms.RequestPermissions

    /// Request all permissions required by this plugin at startup.
    ///
    /// On iOS:
    ///  - Contacts: requests CNContactStore authorization.
    ///    On denial a non-cancellable alert lets the user open Settings or exit.
    ///  - Phone / SMS:  no explicit permission needed (system handles it inline).
    class RequestPermissions: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            DispatchQueue.main.async {
                let store = CNContactStore()
                let status = CNContactStore.authorizationStatus(for: .contacts)

                switch status {
                case .authorized, .limited:
                    // Already granted – nothing to do
                    break
                case .notDetermined:
                    store.requestAccess(for: .contacts) { granted, _ in
                        if !granted {
                            DispatchQueue.main.async {
                                CallsSmsFunctions.showPermissionDeniedAlert()
                            }
                        }
                    }
                case .denied, .restricted:
                    CallsSmsFunctions.showPermissionDeniedAlert()
                @unknown default:
                    break
                }
            }
            return BridgeResponse.success(data: ["success": true, "message": "Permission request initiated"])
        }
    }

    // MARK: - Shared helpers

    /// Present a non-cancellable alert telling the user to open Settings or exit.
    static func showPermissionDeniedAlert() {
        guard let root = UIApplication.shared
            .connectedScenes
            .filter({ $0.activationState == .foregroundActive })
            .compactMap({ $0 as? UIWindowScene })
            .first?.windows.first(where: { $0.isKeyWindow })?.
            rootViewController else { return }

        let alert = UIAlertController(
            title: "Permissions Required",
            message: "This app needs Contacts access to let you pick phone numbers.\n\n"
                   + "Please enable it in Settings to continue.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Open Settings", style: .default) { _ in
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
        })
        alert.addAction(UIAlertAction(title: "Exit App", style: .destructive) { _ in
            exit(0)
        })
        // Not cancellable – user must choose
        root.present(alert, animated: true)
    }
}
