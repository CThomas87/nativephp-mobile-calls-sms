<?php

namespace Openview\Callssms;

use Illuminate\Support\ServiceProvider;

class CallssmsServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(Callssms::class, function () {
            return new Callssms();
        });
    }

    public function boot(): void
    {
        //
    }
}
