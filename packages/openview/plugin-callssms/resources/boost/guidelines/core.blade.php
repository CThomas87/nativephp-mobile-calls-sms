## openview/plugin-callssms

NativePHP Mobile plugin for native phone calls, SMS, WhatsApp integration, and a contact picker on Android and iOS.

### Installation

```bash
composer require openview/plugin-callssms
php artisan native:plugin:register openview/plugin-callssms
```

### Required Permissions

**Android**  `CALL_PHONE` (for auto-dial) and `READ_CONTACTS` (for contact picker) are requested at runtime automatically.
**iOS**  `NSContactsUsageDescription` and `LSApplicationQueriesSchemes` are added automatically via `nativephp.json`.

---

### PHP Usage (Livewire / Blade)

Use the `Callssms` facade:

@verbatim
<code-snippet name="Open phone dialler" lang="php">
use Openview\Callssms\Facades\Callssms;

// Open dialler pre-filled (user taps call)
Callssms::openPhoneDialer('+15551234567');

// Auto-dial immediately (requests CALL_PHONE permission first)
Callssms::openPhoneDialer('+15551234567', autoCall: true);
</code-snippet>
@endverbatim

@verbatim
<code-snippet name="Open SMS composer" lang="php">
use Openview\Callssms\Facades\Callssms;

Callssms::openSms('+15551234567');
Callssms::openSms('+15551234567', 'Hello from the app!');
</code-snippet>
@endverbatim

@verbatim
<code-snippet name="Open WhatsApp" lang="php">
use Openview\Callssms\Facades\Callssms;

// Open WhatsApp chat
Callssms::openWhatsapp('+15551234567');
Callssms::openWhatsapp('+15551234567', 'Hey!', mode: 'message');

// Navigate to the WhatsApp contact so the user can place a call
Callssms::openWhatsapp('+15551234567', mode: 'call');
</code-snippet>
@endverbatim

@verbatim
<code-snippet name="Pick a contact (Livewire)" lang="php">
use Livewire\Component;
use Native\Mobile\Attributes\OnNative;
use Openview\Callssms\Events\ContactSelected;
use Openview\Callssms\Facades\Callssms;

class DialerComponent extends Component
{
    public string $name  = '';
    public string $phone = '';

    public function pickContact(): void
    {
        Callssms::pickContact();           // device contacts
        // Callssms::pickContact('whatsapp'); // WhatsApp contacts
    }

    #[OnNative(ContactSelected::class)]
    public function handleContactSelected(string $name, string $phone, string $source): void
    {
        $this->name  = $name;
        $this->phone = $phone;
        // $source is 'device' or 'whatsapp'
    }

    public function render()
    {
        return view('livewire.dialer-component');
    }
}
</code-snippet>
@endverbatim

---

### Available PHP Facade Methods

- `Callssms::openPhoneDialer(string $phone, bool $autoCall = false): bool`
  Opens the native phone dialler. Returns true on success. Set `$autoCall = true` to place the call immediately (requires and requests CALL_PHONE permission).

- `Callssms::openSms(string $phone, string $message = ''): bool`
  Opens the native SMS composer. Returns true on success.

- `Callssms::openWhatsapp(string $phone, string $message = '', string $mode = 'message'): bool`
  Opens WhatsApp. `$mode` is `'message'` (default) or `'call'`. Falls back to `wa.me` browser URL if WhatsApp is not installed.

- `Callssms::pickContact(string $source = 'device'): void`
  Launches the contact picker. The result is dispatched asynchronously as the `ContactSelected` event  there is no return value. `$source` is `'device'` or `'whatsapp'`.

---

### Events

**`Openview\Callssms\Events\ContactSelected`**

Dispatched when the user selects a contact. Listen with `#[OnNative(ContactSelected::class)]`.

Payload parameters:
- `string $name`  display name of the contact
- `string $phone`  phone number as stored in the contact
- `string $source`  `'device'` or `'whatsapp'`

---

### JavaScript Usage (Vue / React / Inertia)

@verbatim
<code-snippet name="Vue Inertia example" lang="javascript">
import { ref, onMounted, onUnmounted } from 'vue';
import { CallsSms, OnContactSelected, OffContactSelected } from '@openview/plugin-callssms';

const picked = ref(null);

function handler({ name, phone, source }) {
    picked.value = { name, phone, source };
}

onMounted(() => OnContactSelected(handler));
onUnmounted(() => OffContactSelected(handler));

// Call bridge functions
await CallsSms.openPhoneDialer('+15551234567');
await CallsSms.openPhoneDialer('+15551234567', { autoCall: true });
await CallsSms.openSms('+15551234567', { message: 'Hello!' });
await CallsSms.openWhatsapp('+15551234567', { mode: 'message' });
await CallsSms.openWhatsapp('+15551234567', { mode: 'call' });
await CallsSms.pickContact({ source: 'device' });
await CallsSms.pickContact({ source: 'whatsapp' });
</code-snippet>
@endverbatim

---

### Common Patterns and Gotchas

- **Phone number format**: Use international format with `+` prefix (e.g., `+15551234567`) for WhatsApp. The phone dialler and SMS accept any local format.
- **Auto-call on Android**: `autoCall: true` requires the `CALL_PHONE` runtime permission. The plugin requests it automatically; if denied, it falls back to opening the dialler without dialling.
- **WhatsApp not installed**: `openWhatsapp()` always falls back to opening `wa.me` in the browser  it never throws an error.
- **Contact picker is async**: `pickContact()` / `Callssms::pickContact()` returns immediately; the selected contact arrives later as a `ContactSelected` event. Always subscribe to the event before calling `pickContact()`.
- **WhatsApp contacts on iOS**: The WhatsApp contacts picker deep-link is not available on iOS. Passing `source: 'whatsapp'` on iOS falls back to the system contacts picker.
- **Permissions on startup**: Call `Callssms.RequestPermissions` (bridge) or use the `RequestPermissions` bridge function at app launch to pre-request all required permissions before the user needs them.

---

### Bridge Function Names (for raw BridgeCall usage)

- `Callssms.OpenPhoneDialer`  params: `{ phone: string, autoCall?: boolean }`
- `Callssms.OpenSms`          params: `{ phone: string, message?: string }`
- `Callssms.OpenWhatsapp`     params: `{ phone: string, message?: string, mode?: 'message'|'call' }`
- `Callssms.PickContact`      params: `{ source?: 'device'|'whatsapp' }`
- `Callssms.RequestPermissions`  params: `{}`  requests CALL_PHONE and READ_CONTACTS; exits app if denied
