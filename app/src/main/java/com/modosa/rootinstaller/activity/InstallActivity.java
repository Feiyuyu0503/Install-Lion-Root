package com.modosa.rootinstaller.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.modosa.rootinstaller.R;
import com.modosa.rootinstaller.util.ResultUtil;
import com.modosa.rootinstaller.util.ShellUtil;
import com.modosa.rootinstaller.util.apksource.ApkSource;
import com.modosa.rootinstaller.util.installer.ApkSourceBuilder;
import com.modosa.rootinstaller.util.installer.SAIPackageInstaller;
import com.modosa.rootinstaller.util.installer.rooted.RootedSAIPackageInstaller;
import com.modosa.rootinstaller.util.shell.Shell;
import com.modosa.rootinstaller.util.shell.SuShell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * @author dadaewq
 */
public class InstallActivity extends AbstractInstallActivity implements SAIPackageInstaller.InstallationStatusListener {

    private ArrayList<File> files;
    private long mOngoingSessionId;
    private String installApkPath;
    private String uninstallPkgName;

    @Override
    public void startInstall(String getinstallApkPath) {
        Log.d("Start install", getinstallApkPath + "");
        if (getinstallApkPath != null) {
            installApkPath = getinstallApkPath;
            installApkFile = new File(installApkPath);

            files = new ArrayList<>();
            files.add(installApkFile);

            new InstallApkTask().start();
        }
    }

    @Override
    protected void startUninstall(String getUninstallPkgName) {
        this.uninstallPkgName = getUninstallPkgName;
        new UninstallApkTask().start();
    }


    private void installPackages(List<File> apkFiles) {
        Context mContext = getApplication();
        SAIPackageInstaller mInstaller = RootedSAIPackageInstaller.getInstance(mContext);
        mInstaller.addStatusListener(this);
        ApkSource apkSource = new ApkSourceBuilder()
                .fromApkFiles(apkFiles)
                .build();

        mOngoingSessionId = mInstaller.createInstallationSession(apkSource);
        mInstaller.startInstallationSession(mOngoingSessionId);
    }


    @Override
    public void onStatusChanged(long installationId, SAIPackageInstaller.InstallationStatus status, @Nullable String packageNameOrErrorDescription) {
        if (installationId != mOngoingSessionId) {
            deleteCache();
            return;
        }
        Log.d("status", status + "");

        switch (status) {
            case QUEUED:
            case INSTALLING:
                break;
            case INSTALLATION_SUCCEED:
                deleteCache();
                showMyToast0(String.format(getString(R.string.tip_success_install), apkinfo[0]));
                showNotification(true);
                finish();
                break;
            case INSTALLATION_FAILED:

                if (packageNameOrErrorDescription == null) {
                    showNotification(false);
                    deleteCache();
                    copyErr(getString(R.string.unknown));
                    showMyToast1(String.format(getString(R.string.tip_failed_install_witherror), apkinfo[0], ""));
                } else {
                    if (packageNameOrErrorDescription.contains(getString(R.string.installer_error_root_no_root))) {
                        installByShellUtil();
                    } else {
                        showNotification(false);
                        deleteCache();
                        copyErr(packageNameOrErrorDescription);
                        String err = packageNameOrErrorDescription.substring(packageNameOrErrorDescription.indexOf("Err:") + 4);
                        showMyToast1(String.format(getString(R.string.tip_failed_install_witherror), apkinfo[0], err));
                    }


                }
                finish();
                break;
            default:
                finish();
        }
    }

    private void showNotification(boolean success) {
        if (show_notification) {
            Log.e("packagename", apkinfo[1]);
            Intent intent = new Intent()
                    .setComponent(new ComponentName(this, NotifyActivity.class))
                    .putExtra("packageName", apkinfo[1])
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (success) {
                intent.putExtra("channelId", "1")
                        .putExtra("channelName", getString(R.string.tip_success_install))
                        .putExtra("contentTitle", String.format(getString(R.string.tip_success_install), apkinfo[0]));
            } else {
                intent.putExtra("channelId", "4")
                        .putExtra("channelName", getString(R.string.channalname_fail))
                        .putExtra("realPath", installApkPath)
                        .putExtra("contentTitle", String.format(getString(R.string.tip_failed_install), apkinfo[0]));
            }
            startActivity(intent);
        }


    }

    private void installByShellUtil() {
        String installcommand = "pm install -r -d --user 0 -i " + getPackageName() + " \"" + installApkPath + "\"";

        String[] resultselinux = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resultselinux = ShellUtil.execWithRoot("setenforce 0");
        }
        Log.e("installcommand", installcommand);

        String[] result = ShellUtil.execWithRoot(installcommand);


        if ("0".equals(result[3])) {
            deleteCache();
            showNotification(true);
            showMyToast0(String.format(getString(R.string.tip_success_install), apkinfo[0]));
        } else {
            showNotification(false);
            deleteCache();
            String installerVersion = "???";
            try {
                installerVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException ignore) {
            }
            StringBuilder err = new StringBuilder(String.format("%s: %s %s | %s | Android %s | Install Lion-Root %s\n\n", getString(R.string.installer_device), Build.BRAND, Build.MODEL, ResultUtil.isMiui() ? "MIUI" : " ", Build.VERSION.RELEASE, installerVersion))
                    .append(String.format("Command: %s\nExit code: %s\nOut:\n%s\n=============\nErr:\n%s", result[2], result[3], result[0], result[1]));

            if (resultselinux != null && !"0".equals(resultselinux[3])) {
                copyErr(err.append("\n") + resultselinux[1]);

                int i = 0;
                for (String key : resultselinux
                ) {
                    i++;
                    Log.e(i + " ", key + "");
                }
                showMyToast1(String.format(getString(R.string.tip_failed_install_witherror), apkinfo[0], resultselinux[1] + "\n" + result[1]));
            } else {
                copyErr(err.toString());
                showMyToast1(String.format(getString(R.string.tip_failed_install_witherror), apkinfo[0], result[1]));
            }

        }
    }

    private class InstallApkTask extends Thread {
        @Override
        public void run() {
            super.run();
            if (SuShell.getInstance().isAvailable()) {
                showMyToast0(String.format(getString(R.string.start_install), apkinfo[0]));
                try {
                    installPackages(files);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                deleteCache();
                showMyToast1(String.format(getString(R.string.tip_failed_install), getString(R.string.installer_error_root_no_root)));
            }
        }
    }


    private class UninstallApkTask extends Thread {
        @Override
        public void run() {
            super.run();
            Log.d("Start uninstall", uninstallPkgName);

            if (SuShell.getInstance().isAvailable()) {
                Shell.Result uninstallationResult = SuShell.getInstance().exec(new Shell.Command("pm", "uninstall", uninstallPkgName));
                if (0 == uninstallationResult.exitCode) {
                    showMyToast0(String.format(getString(R.string.tip_success_uninstall), uninstallPackageLable));
                } else {
                    String saiVersion = "???";
                    try {
                        saiVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (PackageManager.NameNotFoundException ignore) {
                    }
                    String info = String.format("%s: %s %s | %s | Android %s | Install Lion-Root %s\n\n", getString(R.string.installer_device), Build.BRAND, Build.MODEL, ResultUtil.isMiui() ? "MIUI" : " ", Build.VERSION.RELEASE, saiVersion);
                    copyErr(info + uninstallationResult.toString());
                    showMyToast1(String.format(getString(R.string.tip_failed_uninstall_witherror), uninstallPackageLable, uninstallationResult.err));
                }
            } else {
                showMyToast1(String.format(getString(R.string.tip_failed_uninstall), getString(R.string.installer_error_root_no_root)));
            }
        }
    }
}
