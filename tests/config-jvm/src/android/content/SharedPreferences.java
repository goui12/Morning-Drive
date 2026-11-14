package android.content;

/** Signatures used by production Config, backed by an atomic in-memory test implementation. */
public interface SharedPreferences {
    String getString(String key,String fallback);
    int getInt(String key,int fallback);
    long getLong(String key,long fallback);
    boolean getBoolean(String key,boolean fallback);
    Editor edit();
    interface Editor {
        Editor putString(String key,String value);
        Editor putInt(String key,int value);
        Editor putLong(String key,long value);
        Editor putBoolean(String key,boolean value);
        Editor clear();
        boolean commit();
        void apply();
    }
}
