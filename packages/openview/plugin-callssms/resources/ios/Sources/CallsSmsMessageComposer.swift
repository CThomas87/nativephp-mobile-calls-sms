import Foundation
import UIKit
import MessageUI

// MARK: - CallsSmsMessageComposer

/// Presents a `MFMessageComposeViewController` (the native iOS Messages compose
/// sheet) pre-filled with a recipient and optional body text.
///
/// This is the recommended Apple approach for in-app SMS composition; it gives
/// the user the familiar native compose UI and handles sending internally without
/// requiring any special permissions.
///
/// Usage (from a BridgeFunction):
/// ```swift
/// DispatchQueue.main.async {
///     CallsSmsMessageComposer.shared.present(recipient: "+15551234567", body: "Hello!")
/// }
/// ```
class CallsSmsMessageComposer: NSObject {

    static let shared = CallsSmsMessageComposer()

    private override init() { super.init() }

    // MARK: Public

    /// Present the native SMS compose sheet.
    /// - Parameters:
    ///   - recipient: Phone number to pre-fill in the To field.
    ///   - body:      Message text to pre-fill in the compose area.
    func present(recipient: String, body: String) {
        guard MFMessageComposeViewController.canSendText() else {
            print("⚠️ CallsSmsMessageComposer: device cannot send SMS – falling back to URL scheme")
            openSmsUrl(recipient: recipient, body: body)
            return
        }

        guard let root = keyWindowRootViewController() else {
            print("❌ CallsSmsMessageComposer: cannot find root view controller")
            return
        }

        let controller = MFMessageComposeViewController()
        controller.messageComposeDelegate = self
        controller.recipients = [recipient]
        controller.body       = body

        root.present(controller, animated: true, completion: nil)
    }

    // MARK: Private helpers

    private func openSmsUrl(recipient: String, body: String) {
        let encodedBody = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let raw = encodedBody.isEmpty ? "sms:\(recipient)" : "sms:\(recipient)&body=\(encodedBody)"
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    private func keyWindowRootViewController() -> UIViewController? {
        UIApplication.shared
            .connectedScenes
            .filter { $0.activationState == .foregroundActive }
            .compactMap { $0 as? UIWindowScene }
            .first?
            .windows
            .first { $0.isKeyWindow }?
            .rootViewController?
            .topmostViewController()
    }
}

// MARK: - MFMessageComposeViewControllerDelegate

extension CallsSmsMessageComposer: MFMessageComposeViewControllerDelegate {

    func messageComposeViewController(
        _ controller: MFMessageComposeViewController,
        didFinishWith result: MessageComposeResult
    ) {
        switch result {
        case .sent:      print("✅ CallsSmsMessageComposer: message sent")
        case .cancelled: print("⚠️ CallsSmsMessageComposer: cancelled by user")
        case .failed:    print("❌ CallsSmsMessageComposer: failed to send")
        @unknown default: break
        }
        controller.dismiss(animated: true, completion: nil)
    }
}

// MARK: - UIViewController extension

private extension UIViewController {
    func topmostViewController() -> UIViewController {
        if let presented = presentedViewController {
            return presented.topmostViewController()
        }
        if let nav = self as? UINavigationController {
            return nav.visibleViewController?.topmostViewController() ?? self
        }
        if let tab = self as? UITabBarController {
            return tab.selectedViewController?.topmostViewController() ?? self
        }
        return self
    }
}
