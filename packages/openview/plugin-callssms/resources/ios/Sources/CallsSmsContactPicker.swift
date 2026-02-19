import Foundation
import UIKit
import Contacts
import ContactsUI

// MARK: - CallsSmsContactPicker

/// Presents a `CNContactPickerViewController` on behalf of the CallsSms plugin
/// and dispatches a `ContactSelected` PHP event with the chosen contact's
/// name and phone number.
///
/// Usage (from a BridgeFunction):
/// ```swift
/// DispatchQueue.main.async {
///     CallsSmsContactPicker.shared.present(source: "device")
/// }
/// ```
class CallsSmsContactPicker: NSObject {

    static let shared = CallsSmsContactPicker()

    private var pendingSource: String = "device"

    private override init() { super.init() }

    // MARK: Public

    /// Present the contact picker from the currently active view controller.
    /// - Parameter source: `"device"` or `"whatsapp"` – stored and forwarded
    ///   in the ContactSelected event payload.
    func present(source: String) {
        self.pendingSource = source

        guard let root = keyWindowRootViewController() else {
            print("❌ CallsSmsContactPicker: cannot find root view controller")
            return
        }

        let picker = CNContactPickerViewController()
        picker.delegate = self

        // Only contacts with at least one phone number are selectable
        picker.predicateForEnablingContact = NSPredicate(
            format: "phoneNumbers.@count > 0"
        )
        // Show name and phone fields in the picker detail rows
        picker.displayedPropertyKeys = [
            CNContactGivenNameKey,
            CNContactFamilyNameKey,
            CNContactPhoneNumbersKey,
        ]

        root.present(picker, animated: true, completion: nil)
    }

    // MARK: Private helpers

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

    private func dispatchContactSelected(name: String, phone: String, source: String) {
        let eventClass = "Openview\\Callssms\\Events\\ContactSelected"
        let payload: [String: Any] = [
            "name":   name,
            "phone":  phone,
            "source": source,
        ]

        print("📤 CallsSmsContactPicker dispatching \(eventClass) – name=\(name), phone=\(phone), source=\(source)")
        LaravelBridge.shared.send?(eventClass, payload)
    }
}

// MARK: - CNContactPickerDelegate

extension CallsSmsContactPicker: CNContactPickerDelegate {

    /// User cancelled without selecting.
    func contactPickerDidCancel(_ picker: CNContactPickerViewController) {
        print("⚠️ CallsSmsContactPicker: cancelled by user")
        picker.dismiss(animated: true, completion: nil)
    }

    /// A specific phone number property was selected (best case – single number chosen).
    func contactPicker(
        _ picker: CNContactPickerViewController,
        didSelect contactProperty: CNContactProperty
    ) {
        defer { picker.dismiss(animated: true, completion: nil) }

        guard let phoneNumber = contactProperty.value as? CNPhoneNumber else { return }

        let contact = contactProperty.contact
        let name    = fullName(from: contact)
        let phone   = phoneNumber.stringValue

        dispatchContactSelected(name: name, phone: phone, source: pendingSource)
    }

    /// A contact was selected (no specific number chosen; use first phone).
    func contactPicker(
        _ picker: CNContactPickerViewController,
        didSelect contact: CNContact
    ) {
        defer { picker.dismiss(animated: true, completion: nil) }

        guard let first = contact.phoneNumbers.first else {
            print("⚠️ CallsSmsContactPicker: selected contact has no phone numbers")
            return
        }

        let name  = fullName(from: contact)
        let phone = first.value.stringValue

        dispatchContactSelected(name: name, phone: phone, source: pendingSource)
    }

    // MARK: Helpers

    private func fullName(from contact: CNContact) -> String {
        let parts = [contact.givenName, contact.familyName].filter { !$0.isEmpty }
        return parts.isEmpty ? contact.organizationName : parts.joined(separator: " ")
    }
}

// MARK: - UIViewController extension

private extension UIViewController {
    /// Walk the presented-controller chain to reach the topmost visible controller.
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
