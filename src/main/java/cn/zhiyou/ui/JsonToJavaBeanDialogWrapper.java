package cn.zhiyou.ui;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhiyou.enums.LombokAnnotationEnum;
import cn.zhiyou.ui.basic.MultiRowLanguageTextField;
import cn.zhiyou.ui.basic.TextFieldErrorPopupDecorator;
import cn.zhiyou.utils.ActionUtil;
import cn.zhiyou.utils.CodeCreateUtil;
import cn.zhiyou.utils.CommonUtil;
import cn.zhiyou.utils.NotificationUtil;
import cn.zhiyou.validator.ClassValidator;
import com.intellij.json.json5.Json5Language;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.EditorTextField;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author wcp
 * @since 2023/12/11
 */
public class JsonToJavaBeanDialogWrapper extends DialogWrapper {
    private JPanel rootPanel;
    private JTextField classNameTextField;
    private EditorTextField jsonEditorTextField;

    private final Project project;
    private final PsiDirectory directory;
    private final Module module;

    private final TextFieldErrorPopupDecorator classNameerrorPopupDecorator;
    private final TextFieldErrorPopupDecorator jsonErrorPopupDecorator;

    public JsonToJavaBeanDialogWrapper(Project project, PsiDirectory directory, Module module) {
        super(project, true);
        this.project = project;
        this.directory = directory;
        this.module = module;

        this.classNameerrorPopupDecorator = new TextFieldErrorPopupDecorator(getRootPane(), classNameTextField);
        this.jsonErrorPopupDecorator = new TextFieldErrorPopupDecorator(getRootPane(), jsonEditorTextField);

        getWindow().addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                classNameTextField.requestFocusInWindow();
            }
        });

        // 非模态弹窗（可以在展示弹窗时，不限制主页面的操作）
        setModal(false);

        setOKButtonText("生成");
        setCancelButtonText("取消");

        init();
        setTitle("Json转JavaBean");
    }

    private void createUIComponents() {
        // 声明 Json 类型编辑器
        jsonEditorTextField = new MultiRowLanguageTextField(Json5Language.INSTANCE, project, "", true);
        jsonEditorTextField.setFont(new Font("Consolas", Font.PLAIN, 15));
        jsonEditorTextField.setPlaceholder("请输入JSON");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return rootPanel;
    }

    @Override
    protected void doOKAction() {
        if (getOKAction().isEnabled()) {
            // 执行逻辑
            if (executeOkAction()) {
                applyFields();
                close(OK_EXIT_CODE);
            }
        }
    }


    private boolean executeOkAction() {
        // 获取 Json 文本
        String jsonText = StrUtil.trim(jsonEditorTextField.getText());
        if (StringUtils.isBlank(jsonText)) {
            // 为空则提示
            jsonErrorPopupDecorator.setError("无效JSON");
            return false;
        }

        // 获取 ClassName
        String className = classNameTextField.getText();
        if (StringUtils.isBlank(className)) {
            className = "JsonGenerate" + IdUtil.simpleUUID().substring(0, 4);
        } else {
            // 存在名字，校验
            ClassValidator classValidator = new ClassValidator(project, directory);
            if (!(classValidator.checkInput(className) && classValidator.canClose(className))) {
                classNameerrorPopupDecorator.setError(classValidator.getErrorText(className));
                return false;
            }
        }

        // 判断文件是否已经存在
        if (directory.findFile(className + ".java") != null) {
            // 提示
            classNameerrorPopupDecorator.setError(StrUtil.format("{} 类已经存在", className));
            return false;
        }

        JSONObject jsonObject;

        // 解析Json
        if (!CommonUtil.isJson(jsonText)) {
            jsonErrorPopupDecorator.setError("无效JSON");
            return false;
        }

        if (CommonUtil.isJsonArray(jsonText)) {
            JSONArray jsonArray = JSONUtil.parseArray(jsonText);
            // 数组为空
            if (jsonArray.isEmpty()) {
                jsonErrorPopupDecorator.setError("无效JSON");
                return false;
            }

            jsonObject = jsonArray.getJSONObject(0);
        } else if (CommonUtil.isJsonObject(jsonText)) {
            jsonObject = JSONUtil.parseObj(jsonText);
        } else {
            jsonObject = null;
        }

        // 属性为空
        if (Objects.isNull(jsonObject) || jsonObject.isEmpty()) {
            jsonErrorPopupDecorator.setError("无效JSON");
            return false;
        }

        // 先生成Java文件
        JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
        PsiClass newClass = directoryService.createClass(directory, className);

        // 判断是否存在lombok依赖
        boolean hasLibrary = ActionUtil.hasLibrary(module, "org.projectlombok:lombok");

        ActionUtil.runWriteCommandAction(project, () -> {
            // Java元素构建器
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            // 递归添加Json字段
            recursionAddProperty(jsonObject, newClass, factory);
            // 添加lombok注解，给内部类也加上
            if (hasLibrary) {
                // 增加导入
                this.importClass(project, newClass, factory);
            }

            // 刷新文件系统
            ActionUtil.refreshFileSystem();
            // 编辑器定位到新建类
            newClass.navigate(true);
        });

        return true;
    }


    /**
     * 导入类并添加注解
     *
     * @param project  当前项目
     * @param newClass 待导入的类
     * @param factory  用于创建新的psi元素的工厂
     */
    private void importClass(Project project, PsiClass newClass, PsiElementFactory factory) {
        // 增加注解
        PsiAnnotation dataAnnotation = factory.createAnnotationFromText(
                "@" + CommonUtil.qualifiedNameToClassName(LombokAnnotationEnum.DATA.getValue()), null);
        PsiAnnotation accessorsAnnotation = factory.createAnnotationFromText(
                "@" + CommonUtil.qualifiedNameToClassName(LombokAnnotationEnum.ACCESSORS.getValue()) + "(chain = true)", null);

        // 导入类
        ActionUtil.importClassesInClass(project, newClass, LombokAnnotationEnum.DATA.getValue(), LombokAnnotationEnum.ACCESSORS.getValue(), List.class.getName());

        PsiElement firstChild = newClass.getFirstChild();
        if (firstChild instanceof PsiDocComment) {
            newClass.addAfter(accessorsAnnotation, firstChild);
            newClass.addAfter(dataAnnotation, firstChild);
        } else {
            newClass.addBefore(dataAnnotation, firstChild);
            newClass.addBefore(accessorsAnnotation, firstChild);
        }

        // 内部类也加上（要递归）
        this.addImportAnnotationOnInnerClass(newClass, dataAnnotation, accessorsAnnotation);
    }

    private void addImportAnnotationOnInnerClass(PsiClass newClass, PsiAnnotation dataAnnotation, PsiAnnotation accessorsAnnotation) {
        PsiClass[] innerClasses = newClass.getInnerClasses();
        if (ArrayUtil.isNotEmpty(innerClasses)) {
            for (PsiClass innerClass : innerClasses) {
                PsiElement innerFirstChild = innerClass.getFirstChild();
                innerClass.addBefore(dataAnnotation, innerFirstChild);
                innerClass.addBefore(accessorsAnnotation, innerFirstChild);
                // 找内部类中是否还有内部类，并添加上注解
                addImportAnnotationOnInnerClass(innerClass, dataAnnotation, accessorsAnnotation);
            }
        }
    }


    private void recursionAddProperty(JSONObject jsonObject, PsiClass psiClass, PsiElementFactory factory) {
        // 循环所有Json字段
        for (Map.Entry<String, Object> entry : jsonObject) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // ------------- 如果value是JsonObject，表示是对象
            if (value instanceof JSONObject childJsonObject) {
                // 如果是对象，则还需要创建内部类
                PsiClass innerClass = factory.createClass(StrUtil.upperFirst(key));
                // 则递归添加
                recursionAddProperty(childJsonObject, innerClass, factory);
                // 添加内部类至主类
                psiClass.add(innerClass);
                // 添加当前内部类类型的字段
                String fieldText = StrUtil.format("private {} {};", innerClass.getName(), StrUtil.lowerFirst(key));
                // 构建字段对象
                PsiField psiField = factory.createFieldFromText(fieldText, psiClass);
                // 添加到Class
                psiClass.add(psiField);
            } else if (value instanceof JSONArray jsonArray) {
                if (CollUtil.isNotEmpty(jsonArray)) {
                    JSONObject jsonObj = (JSONObject) jsonArray.get(0);
                    String innerClassName = StrUtil.upperFirst(key + "Bean");
                    // 如果是对象，则还需要创建内部类
                    PsiClass innerClass = factory.createClass(innerClassName);
                    // 则递归添加
                    recursionAddProperty(jsonObj, innerClass, factory);
                    // 添加内部类至主类
                    psiClass.add(innerClass);
                    // 添加当前内部类类型的字段
                    String fieldText = StrUtil.format("private List<{}> {};", innerClass.getName(), StrUtil.lowerFirst(key));
                    // 构建字段对象
                    PsiField psiField = factory.createFieldFromText(fieldText, psiClass);
                    // 添加到Class
                    psiClass.add(psiField);
                }
            } else {
                // ------------- 非对象，则直接添加字段
                // 获取字段类型
                String propertyType = CodeCreateUtil.getStrType(value);
                // 定义字段文本
                String fieldText = StrUtil.format("private {} {};", propertyType, StrUtil.lowerFirst(key));
                // 构建字段对象
                PsiField psiField = factory.createFieldFromText(fieldText, psiClass);
                // 添加到Class
                psiClass.add(psiField);
            }
        }
    }

}
