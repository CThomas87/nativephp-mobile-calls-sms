import { wayfinder } from '@laravel/vite-plugin-wayfinder';
import tailwindcss from '@tailwindcss/vite';
import vue from '@vitejs/plugin-vue';
import laravel from 'laravel-vite-plugin';
import { nativephpHotFile, nativephpMobile } from './vendor/nativephp/mobile/resources/js/vite-plugin.js';
import path from 'path';
import { defineConfig } from 'vite';

export default defineConfig({
    resolve: {
        alias: {
            // NativePHP Mobile bridge (resolved from vendor)
            '@nativephp/mobile': path.resolve(
                __dirname,
                'vendor/nativephp/mobile/resources/dist/native.js'
            ),
            // Calls & SMS plugin JS interface
            '@callssms': path.resolve(
                __dirname,
                'packages/openview/plugin-callssms/resources/js/callssms.js'
            ),
        },
    },
    plugins: [
        nativephpMobile(),
        laravel({
            input: ['resources/js/app.ts'],
            ssr: 'resources/js/ssr.ts',
            refresh: true,
            hotFile: nativephpHotFile(),
        }),
        tailwindcss(),
        vue({
            template: {
                transformAssetUrls: {
                    base: null,
                    includeAbsolute: false,
                },
            },
        }),
        wayfinder({
            formVariants: true,
        }),
    ],
});
