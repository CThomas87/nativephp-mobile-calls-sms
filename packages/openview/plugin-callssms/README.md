# openview/plugin-callssms

A NativePHP Mobile plugin providing native phone calls, SMS composition, WhatsApp integration, and a contact picker for iOS and Android.

## Features

- Open the native phone dialler (with optional auto-call)
- Open the SMS composer with a pre-filled recipient and message
- Open WhatsApp in message or call mode
- Launch the native contact picker (device contacts or WhatsApp)
- Runtime permission handling (CALL_PHONE, READ_CONTACTS on Android; Contacts on iOS)

## Requirements

- PHP 8.2+
- NativePHP Mobile 3.x
- Android 33+ / iOS 18.0+

## Installation

```bash
composer require openview/plugin-callssms
php artisan native:plugin:register openview/plugin-callssms
```

## Required Permissions

### Android

The following permissions are automatically added to your Android manifest:

| Permission | Purpose |
|---|---|
| `CALL_PHONE` | Required when `autoCall: true` is passed to `openPhoneDialer()` |
| `READ_CONTACTS` | Required to query device contacts via `pickContact()` |

Both permissions are requested at runtime on first use. If denied, the user is shown a non-cancellable dialog offering to open Settings or exit the app.

### iOS

Add the following to your `Info.plist` (handled automatically via `nativephp.json`):

| Key | Purpose |
|---|---|
| `NSContactsUsageDescription` | Explain why the app needs contact access |
| `LSApplicationQueriesSchemes` | Whitelist `whatsapp`, `tel`, `telprompt`, `sms` |

## Usage (PHP / Livewire)

```php
use Openview\Callssms\Facades\Callssms;

// Open dialler pre-filled with a number
Callssms::openPhoneDialer('+15551234567');

// Auto-dial immediately (requests CALL_PHONE permission if not yet granted)
Callssms::openPhoneDialer('+15551234567', autoCall: true);

// Open SMS composer
Callssms::openSms('+15551234567');
Callssms::openSms('+15551234567', 'Hello from the app!');

// Open WhatsApp chat
Callssms::openWhatsapp('+15551234567');
Callssms::openWhatsapp('+15551234567', 'Hey there!', mode: 'message');

// Open WhatsApp (navigate to contact for call)
Callssms::openWhatsapp('+15551234567', mode: 'call');

// Launch contact picker  result arrives as ContactSelected event
Callssms::pickContact();           // device contacts
Callssms::pickContact('whatsapp'); // WhatsApp contacts (Android) / device contacts (iOS)
```

### Listening for events (Livewire)

```php
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
        Callssms::pickContact();
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
```

## Usage (JavaScript  Vue / React / Inertia)

Install the JS library (or use it directly via the aliased path configured by NativePHP):

```bash
npm install @openview/plugin-callssms
```

### Vue (Inertia) example

```vue
<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { CallsSms, OnContactSelected, OffContactSelected } from '@openview/plugin-callssms';

const picked = ref(null);

function handler({ name, phone, source }) {
    picked.value = { name, phone, source };
}

onMounted(() => OnContactSelected(handler));
onUnmounted(() => OffContactSelected(handler));

async function dial() {
    await CallsSms.openPhoneDialer('+15551234567');
}

async function sendSms() {
    await CallsSms.openSms('+15551234567', { message: 'Hello!' });
}

async function whatsapp() {
    await CallsSms.openWhatsapp('+15551234567', { mode: 'message' });
}

async function pickContact() {
    await CallsSms.pickContact({ source: 'device' });
}
</script>

<template>
    <button @click="dial">Call</button>
    <button @click="sendSms">SMS</button>
    <button @click="whatsapp">WhatsApp</button>
    <button @click="pickContact">Pick Contact</button>
    <p v-if="picked">{{ picked.name }} - {{ picked.phone }} ({{ picked.source }})</p>
</template>
```

### React example

```jsx
import { useEffect, useState } from 'react';
import { CallsSms, OnContactSelected, OffContactSelected } from '@openview/plugin-callssms';

export default function DialerComponent() {
    const [picked, setPicked] = useState(null);

    useEffect(() => {
        const handler = (data) => setPicked(data);
        OnContactSelected(handler);
        return () => OffContactSelected(handler);
    }, []);

    return (
        <div>
            <button onClick={() => CallsSms.openPhoneDialer('+15551234567')}>Call</button>
            <button onClick={() => CallsSms.openSms('+15551234567', { message: 'Hello!' })}>SMS</button>
            <button onClick={() => CallsSms.openWhatsapp('+15551234567', { mode: 'call' })}>WhatsApp Call</button>
            <button onClick={() => CallsSms.pickContact({ source: 'whatsapp' })}>Pick WhatsApp Contact</button>
            {picked && <p>{picked.name} - {picked.phone} ({picked.source})</p>}
        </div>
    );
}
```

## Available Methods

### PHP Facade (Callssms::)

| Method | Signature | Description |
|---|---|---|
| `openPhoneDialer` | `openPhoneDialer(string $phone, bool $autoCall = false): bool` | Open dialler pre-filled with `$phone`. `$autoCall = true` places the call immediately (requires CALL_PHONE). |
| `openSms` | `openSms(string $phone, string $message = ''): bool` | Open SMS composer for `$phone` with optional `$message` body. |
| `openWhatsapp` | `openWhatsapp(string $phone, string $message = '', string $mode = 'message'): bool` | Open WhatsApp. `$mode` is `'message'` or `'call'`. Falls back to wa.me if not installed. |
| `pickContact` | `pickContact(string $source = 'device'): void` | Launch contact picker. Result arrives via `ContactSelected` event. `$source` is `'device'` or `'whatsapp'`. |

### JavaScript (CallsSms.)

| Function | Signature | Description |
|---|---|---|
| `openPhoneDialer` | `openPhoneDialer(phone, { autoCall? }): Promise` | Open native dialler. |
| `openSms` | `openSms(phone, { message? }): Promise` | Open SMS composer. |
| `openWhatsapp` | `openWhatsapp(phone, { message?, mode? }): Promise` | Open WhatsApp. `mode`: `'message'` (default) or `'call'`. |
| `pickContact` | `pickContact({ source? }): Promise` | Launch contact picker. `source`: `'device'` (default) or `'whatsapp'`. |
| `OnContactSelected` | `OnContactSelected(callback): void` | Subscribe to the ContactSelected event. |
| `OffContactSelected` | `OffContactSelected(callback): void` | Unsubscribe from the ContactSelected event. |

## Events

### ContactSelected

**PHP class:** `Openview\Callssms\Events\ContactSelected`
**JS constant:** `Events.ContactSelected`

Dispatched when the user selects a contact via `pickContact()`.

**Payload:**

| Property | Type | Description |
|---|---|---|
| `name` | `string` | Display name of the selected contact |
| `phone` | `string` | Phone number as stored in the contact |
| `source` | `'device' or 'whatsapp'` | Which picker was used |

## Changelog

### 1.0.0

- Initial release
- Native phone dialler (Android + iOS)
- SMS composer (Android + iOS)
- WhatsApp message and call (Android + iOS, with wa.me fallback)
- Contact picker - device and WhatsApp sources (Android + iOS)
- Runtime permission enforcement for CALL_PHONE and READ_CONTACTS

## License

MIT
