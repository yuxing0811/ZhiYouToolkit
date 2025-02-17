package cn.zhiyou.listener;

import cn.zhiyou.config.CodeNoteSetting;
import cn.zhiyou.config.DoNotAskAgainSetting;
import cn.zhiyou.notify.DoNotAskAgainNotificationAction;
import cn.zhiyou.notify.OpenHelpNotificationAction;
import cn.zhiyou.utils.NotificationUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * 项目启动
 *
 * @author wcp
 * @since 2024/1/2
 */
public class ProjectOpenInitActivity implements ProjectManagerListener {
    private static final Logger LOG = Logger.getInstance(ProjectOpenInitActivity.class);

    @Override
    @SuppressWarnings("removal")
    public void projectOpened(@NotNull Project project) {
        CodeNoteSetting.initDefaultLabel();

        DoNotAskAgainSetting askAgainSetting = DoNotAskAgainSetting.getInstance(project);
        if (!askAgainSetting.doNotAskAgain) {
            NotificationUtil.notifyWithLink(
                    "知游工具",
                    "使用指南：Help -> About ZhiYou",
                    new NotificationAction[]{new OpenHelpNotificationAction(), new DoNotAskAgainNotificationAction()},
                    NotificationType.INFORMATION,
                    project);
        }
    }
}
