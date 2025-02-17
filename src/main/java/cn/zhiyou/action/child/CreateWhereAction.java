package cn.zhiyou.action.child;

import cn.hutool.core.util.ArrayUtil;
import cn.zhiyou.action.OtherOperationsAction;
import cn.zhiyou.ui.CreateConditionDialogWrapper;
import cn.zhiyou.utils.ActionUtil;
import cn.zhiyou.utils.NotificationUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author wcp
 * @since 2024/3/15
 */
public class CreateWhereAction extends AnAction {

    public CreateWhereAction() {
        super("Create Where (MyBatis)");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        handleCondition(event);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        OtherOperationsAction.mybatisUpdate(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private void handleCondition(AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = ActionUtil.getEditor(event);
        if (Objects.isNull(project)) {
            return;
        }

        boolean needCopy;
        PsiClass psiClass;
        if (ActionUtil.isJavaFile(event)) {
            // 根据类字段生成
            needCopy = true;
            psiClass = ActionUtil.getPsiClass(event);
        } else {
            // 弹窗
            needCopy = false;
            psiClass = ActionUtil.chooseClass(project, "Select Entity");
            if (Objects.isNull(psiClass)) {
                // NotificationUtil.notifyApplication(ACTION_TITLE, "请选择类!", NotificationType.WARNING, project);
                return;
            }
        }

        // 所有属性字段
        PsiField[] fields = ActionUtil.getAllFieldFilterStatic(psiClass);

        // 判空
        if (ArrayUtil.isEmpty(fields)) {
            NotificationUtil.notifyApplication(OtherOperationsAction.ACTION_TITLE, "选择类无任何字段，无法生成!", NotificationType.WARNING, project);
            return;
        }

        // 展示窗
        // new CreateConditionDialogWrapper(event.getProject(), editor, editor.getDocument(), fields, needCopy).show();
        new CreateConditionDialogWrapper(event, event.getProject(), editor, editor.getDocument(), fields, needCopy).show();
    }

}
