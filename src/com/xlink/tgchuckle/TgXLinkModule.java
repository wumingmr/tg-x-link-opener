package com.xlink.tgchuckle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 让 org.telegram.group 里被点击的 x.com / twitter.com 链接改由 app.jjyy.chuckle 打开。
 *
 * Hook 点：org.telegram.messenger.browser.Browser 的全部静态 openUrl(Context, ...) 重载
 * （消息内链接点击 / 长按菜单 / 内置浏览器都会汇聚到这里）。
 */
public final class TgXLinkModule extends XposedModule {

    static final String TAG = "TgXLink";

    /** 被 hook 的应用。 */
    static final String TELEGRAM_PKG = "org.telegram.group";
    /** 用来接管链接的应用与其 dispatch Activity（exported=true，处理 x.com 的 http/https VIEW）。 */
    static final String CHUCKLE_PKG = "app.jjyy.chuckle";
    static final String CHUCKLE_ACTIVITY = "app.jjyy.chuckle.platform.twitter.TwitterDispatchActivity";
    /** 链接打开逻辑所在的类。 */
    private static final String BROWSER_CLASS = "org.telegram.messenger.browser.Browser";

    private boolean hooked;
    private int hookCount;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "module loaded, process=" + param.getProcessName()
                + ", api=" + getApiVersion() + ", framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!TELEGRAM_PKG.equals(param.getPackageName())) {
            return;
        }
        installHooks(param.getDefaultClassLoader());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TELEGRAM_PKG.equals(param.getPackageName())) {
            return;
        }
        installHooks(param.getClassLoader());
    }

    private synchronized void installHooks(ClassLoader loader) {
        if (hooked || loader == null) {
            return;
        }
        try {
            Class<?> browser = Class.forName(BROWSER_CLASS, false, loader);
            for (Method method : browser.getDeclaredMethods()) {
                if (!"openUrl".equals(method.getName()) || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 0 || params[0] != Context.class) {
                    continue;
                }
                try {
                    hook(method)
                            .setPriority(XposedInterface.PRIORITY_DEFAULT)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(new OpenUrlHooker());
                    hookCount++;
                    log(Log.INFO, TAG, "hooked " + method.getName() + "(" + params.length + " args)");
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "hook failed for " + method + ": " + t);
                }
            }
            hooked = hookCount > 0;
            log(Log.INFO, TAG, "install done, hooks=" + hookCount);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "cannot hook " + BROWSER_CLASS + ": " + t);
        }
    }

    /** 只关心 x.com / twitter.com 的 http(s) 链接。 */
    static boolean isTargetUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("x.com") || host.endsWith(".x.com")
                || host.equals("twitter.com") || host.endsWith(".twitter.com");
    }

    /** 用显式组件把链接交给 chuckle 的 dispatch Activity。 */
    static Intent buildChuckleIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setComponent(new ComponentName(CHUCKLE_PKG, CHUCKLE_ACTIVITY));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private final class OpenUrlHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            try {
                Object contextArg = chain.getArg(0);
                Object uriArg = chain.getArg(1);
                if (contextArg instanceof Context && uriArg instanceof Uri) {
                    Uri uri = (Uri) uriArg;
                    if (isTargetUri(uri)) {
                        ((Context) contextArg).startActivity(buildChuckleIntent(uri));
                        log(Log.INFO, TAG, "redirect " + uri + " -> " + CHUCKLE_PKG);
                        return null;
                    }
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "intercept failed, fall through: " + t);
            }
            return chain.proceed();
        }
    }
}
