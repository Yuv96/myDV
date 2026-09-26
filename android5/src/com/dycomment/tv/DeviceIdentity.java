package com.dycomment.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.UUID;

/** Installation-scoped Desktop fallback identity; this is not a registered server DID. */
final class DeviceIdentity {
    private DeviceIdentity() {}

    /** Matches reference-im/src/store/device-id.ts, including UTF-16 and unsigned overflow. */
    static String guidDeviceId(String guid) {
        int hash = 0;
        for (int index = 0; index < guid.length(); index++) hash = 31 * hash + guid.charAt(index);
        return Long.toString(hash & 0xffffffffL);
    }

    static synchronized String fallbackDeviceId(Context context) throws IOException {
        SharedPreferences preferences =
                context.getSharedPreferences("dy_device_identity", Context.MODE_PRIVATE);
        String guid = preferences.getString("installation_guid", "");
        if (guid == null || !guid.matches("[0-9a-f]{32}"))
            guid = UUID.randomUUID().toString().replace("-", "");
        // Commit before network use. Recommit also retries a previous failed disk write.
        if (!preferences.edit().putString("installation_guid", guid).commit())
            throw new IOException("无法保存本机分享设备身份，请稍后重试");
        return guidDeviceId(guid);
    }
}
