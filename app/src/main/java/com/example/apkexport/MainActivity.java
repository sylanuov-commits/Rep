package com.example.apkexport;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private final List<ApplicationInfo> apps = new ArrayList<>();
    private ApplicationInfo selected;
    private LinearLayout list;
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24);
        TextView title = new TextView(this); title.setText("Экспорт установленных приложений"); title.setTextSize(22); root.addView(title);
        status = new TextView(this); status.setText("Выберите приложение, затем папку для сохранения APK."); root.addView(status);
        EditText search = new EditText(this); search.setHint("Поиск приложений"); root.addView(search);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll); setContentView(root);
        apps.addAll(getPackageManager().getInstalledApplications(0));
        apps.sort(Comparator.comparing(a -> getPackageManager().getApplicationLabel(a).toString().toLowerCase(Locale.ROOT)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int count,int after) {} public void onTextChanged(CharSequence s,int st,int before,int count) { render(s.toString()); } public void afterTextChanged(android.text.Editable e) {}
        });
        render("");
    }
    private void render(String query) {
        list.removeAllViews();
        for (ApplicationInfo app : apps) {
            String name = getPackageManager().getApplicationLabel(app).toString();
            if (!(name + " " + app.packageName).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            Button button = new Button(this); button.setAllCaps(false); button.setText(name + "\n" + app.packageName); button.setOnClickListener(v -> {
                selected = app; Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); startActivityForResult(intent, 1);
            }); list.addView(button);
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 1 || result != RESULT_OK || data == null || selected == null) return;
        Uri tree = data.getData(); ApplicationInfo app = selected;
        status.setText("Экспорт: " + app.packageName);
        new Thread(() -> {
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                String[] paths = app.splitSourceDirs == null ? new String[]{app.sourceDir} : new String[app.splitSourceDirs.length + 1];
                paths[0] = app.sourceDir;
                if (app.splitSourceDirs != null) System.arraycopy(app.splitSourceDirs,0,paths,1,app.splitSourceDirs.length);
                for (int i=0; i<paths.length; i++) {
                    String filename = app.packageName + (i == 0 ? "-base" : "-split-" + i) + ".apk";
                    Uri out = DocumentsContract.createDocument(getContentResolver(), parent, "application/vnd.android.package-archive", filename);
                    if (out == null) throw new IOException("Не удалось создать файл " + filename);
                    try (InputStream input = new FileInputStream(paths[i]); OutputStream output = getContentResolver().openOutputStream(out)) {
                        if (output == null) throw new IOException("Не удалось открыть файл");
                        byte[] buf = new byte[65536]; int n; while ((n = input.read(buf)) != -1) output.write(buf,0,n);
                    }
                }
                runOnUiThread(() -> status.setText("Готово: сохранено файлов: " + paths.length));
            } catch (Exception e) { runOnUiThread(() -> status.setText("Ошибка экспорта: " + e.getMessage())); }
        }).start();
    }
}
