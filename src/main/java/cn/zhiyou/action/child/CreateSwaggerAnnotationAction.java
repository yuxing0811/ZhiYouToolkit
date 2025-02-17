package cn.zhiyou.action.child;

import cn.zhiyou.action.CreateAnnotationOnFieldAction;
import cn.zhiyou.enums.SwaggerAnnotationEnum;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author wcp
 * @since 2024/3/15
 */
public class CreateSwaggerAnnotationAction extends AnAction {
    public CreateSwaggerAnnotationAction() {
        super("Swagger Annotation");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        CreateAnnotationOnFieldAction.createAnnotation(e, "生成Swagger注解", "value", SwaggerAnnotationEnum.API_MODEL_PROPERTY.getValue());
    }
}
