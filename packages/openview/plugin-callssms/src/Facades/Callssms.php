<?php

namespace Openview\Callssms\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static bool   openPhoneDialer(string $phone, bool $autoCall = false)
 * @method static bool   openSms(string $phone, string $message = '')
 * @method static bool   openWhatsapp(string $phone, string $message = '', string $mode = 'message')
 * @method static void   pickContact(string $source = 'device')
 *
 * @see \Openview\Callssms\Callssms
 */
class Callssms extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Openview\Callssms\Callssms::class;
    }
}
