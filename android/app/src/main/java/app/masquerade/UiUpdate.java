package app.masquerade;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

final class UiUpdate {
    private static final String ENDPOINT =
            "https://raw.githubusercontent.com/sdmillard/Masquerade/main/updates/android-ui.json";
    private static final String PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA1Bp4ZwVaSRRGjE7HPednHKtnjsMTsY8HmMDEMOEIeacdaERdZBz+1E5Y/YAJWgEwhQbv9o2+5NDAHduuU5wR+lUSZ5pnO5veSnanwTe6Kqhkh1ERTQKzxtxBLZxjbWrMIS3Sbg1uqrgqwlDySLaa2puVd+8HyW4HveQqzqS2CJsaqw96F6bnIFcSgFSwG9KqgtswPVx/0Nzr9OFdFV1CXg8jlXCh5AJJ9+P2e6eN/HPmc3fWlLLs/CTrglsHRqreNEdz9xmDqV+JInJzyL2KMVgWTLblszPcdCTSTGuyi333kNR0Ohk7o1QyI2ydKEHMVC1MnFC1ku47zX/IrDlr700OXRU9wleiNnPqmdNHSwY6U5Z4fXxXTUiQf9Sc9K/UuzlikcZz+wEtQgooAFBjr9mMzrDLCHNZefNpngVtcvb/HDzBdD3H/3S9JV6Hi7IRnGF/FRHJ35Yl0jMVqio+JiFQdBZvEfyTwF+grl09fAJv1I8I1fpKg7jaLl+7XLjhAgMBAAE=";
    private static final int MAX_BYTES = 256 * 1024;
    private static final int BUILTIN_VERSION = 2;

    final int version;
    final String html;
    private UiUpdate(int version, String html) {
        this.version = version;
        this.html = html;
    }

    static UiUpdate load(Context context) {
        try (InputStream input = cache(context).openRead()) {
            UiUpdate update = verify(readBounded(input));
            if (update.version > BUILTIN_VERSION) return update;
        } catch (Exception ignored) {
            // A missing or damaged cache falls back to the built-in interface.
        }
        return new UiUpdate(BUILTIN_VERSION, null);
    }

    static synchronized void check(Context context) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != 200) return;
            byte[] envelope;
            try (InputStream input = connection.getInputStream()) {
                envelope = readBounded(input);
            }
            if (verify(envelope).version <= load(context).version) return;
            AtomicFile cache = cache(context);
            FileOutputStream output = cache.startWrite();
            try {
                output.write(envelope);
                cache.finishWrite(output);
            } catch (IOException failure) {
                cache.failWrite(output);
            }
        } catch (Exception ignored) {
            // The app can continue using its last verified interface offline.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static AtomicFile cache(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "verified-ui.json"));
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("UI update too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static UiUpdate verify(byte[] data) throws Exception {
        JSONObject envelope = new JSONObject(new String(data, StandardCharsets.UTF_8));
        byte[] payload = Base64.decode(envelope.getString("payload"), Base64.DEFAULT);
        byte[] key = Base64.decode(PUBLIC_KEY, Base64.DEFAULT);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
        verifier.update(payload);
        if (!verifier.verify(Base64.decode(envelope.getString("signature"), Base64.DEFAULT))) {
            throw new IOException("Invalid UI signature");
        }
        JSONObject ui = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        String html = ui.getString("html");
        if (html.trim().isEmpty()) throw new IOException("Empty UI bundle");
        return new UiUpdate(ui.getInt("version"), html);
    }
}
