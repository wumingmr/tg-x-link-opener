package com.xlink.tgchuckle;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** 模块的说明界面（模块自身不会被 hook，这里只展示配置信息）。 */
public final class InfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        int pad = (int) (getResources().getDisplayMetrics().density * 20);
        text.setPadding(pad, pad, pad, pad);
        text.setTextSize(14f);
        text.setText(
                "TG X 链接 → Chuckle\n\n"
                        + "作用应用：" + TgXLinkModule.TELEGRAM_PKG + "\n\n"
                        + "在 Telegram 中点击 x.com / twitter.com 的 http(s) 链接时，"
                        + "拦截 org.telegram.messenger.browser.Browser.openUrl(...)，"
                        + "改为用显式组件启动：\n"
                        + TgXLinkModule.CHUCKLE_PKG + "/" + TgXLinkModule.CHUCKLE_ACTIVITY + "\n\n"
                        + "使用方式：\n"
                        + "1. 在 LSPosed 中启用本模块；\n"
                        + "2. 作用域勾选 " + TgXLinkModule.TELEGRAM_PKG + "（默认已勾选）；\n"
                        + "3. 强制停止 Telegram 后重新打开；\n"
                        + "4. 点击形如 https://x.com/xxx/status/123 的链接验证。\n\n"
                        + "如未生效，可执行以下命令查看日志：\n"
                        + "logcat -s TgXLink"
        );
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);
    }
}
