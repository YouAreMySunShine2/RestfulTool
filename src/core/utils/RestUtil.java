/*
  Copyright (C), 2018-2020, ZhangYuanSheng
  FileName: RestUtil
  Author:   ZhangYuanSheng
  Date:     2020/5/4 15:14
  Description: 
  History:
  <author>          <time>          <version>          <desc>
  作者姓名            修改时间           版本号              描述
 */
package core.utils;

import cn.hutool.core.util.ReUtil;
import com.intellij.lang.jvm.annotation.*;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import core.beans.PropertiesKey;
import core.beans.Request;
import core.utils.scanner.JaxrsHelper;
import core.utils.scanner.SpringHelper;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author ZhangYuanSheng
 * @version 1.0
 */
public class RestUtil {

    /**
     * 扫描服务端口
     *
     * @param project project
     * @param scope   scope
     * @return port
     */
    public static int scanListenerPort(@NotNull Project project, @NotNull GlobalSearchScope scope) {
        // listener of default server port
        int port = 8080;

        try {
            String value = getConfigurationValue(
                    project, scope,
                    "server.port"
            );
            if (value == null || "".equals((value = value.trim()))) {
                throw new NumberFormatException();
            }
            port = Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
        }
        return port;
    }

    /**
     * 扫描服务协议
     *
     * @param project project
     * @param scope   scope
     * @return protocol
     */
    @NotNull
    public static String scanListenerProtocol(@NotNull Project project, @NotNull GlobalSearchScope scope) {
        // default protocol
        String protocol = "http";

        try {
            String value = getConfigurationValue(project, scope, "server.ssl.enabled");
            if (value == null || "".equals((value = value.trim()))) {
                throw new Exception();
            }
            if (Boolean.parseBoolean(value)) {
                protocol = "https";
            }
        } catch (Exception ignore) {
        }
        return protocol;
    }

    /**
     * 扫描请求路径前缀
     *
     * @param project project
     * @param scope   scope
     * @return path
     */
    @Nullable
    public static String scanContextPath(@NotNull Project project, @NotNull GlobalSearchScope scope) {
        // server.servlet.context-path
        try {
            String contextPath = getConfigurationValue(
                    project, scope,
                    "server.servlet.context-path"
            );
            @Language("RegExp") final String mavenPropReg = "@\\S+@";
            @Nullable String mavenProp;
            if (contextPath != null && (mavenProp = ReUtil.getGroup0(mavenPropReg, contextPath)) != null) {
                Document pomDoc = getModulePomFile(((ModuleWithDependenciesScope) scope).getModule());
                if (pomDoc != null) {
                    Element properties = pomDoc.getRootElement().element("properties");
                    if (properties != null) {
                        Element propItemElement = properties.element(
                                mavenProp.substring(mavenProp.indexOf("@") + 1, mavenProp.lastIndexOf("@"))
                        );
                        if (propItemElement != null) {
                            String name = propItemElement.getData().toString().trim();
                            mavenProp = getPomFileProperties(properties, name);
                            // 如果<properties>找不到则到根标签<project>寻找
                            mavenProp = getPomFileProject(pomDoc.getRootElement(), mavenProp);
                            if (StringUtil.isEmptyOrSpaces(mavenProp)) {
                                return null;
                            }
                        }
                    }
                }
                contextPath = ReUtil.replaceAll(contextPath, mavenPropReg, mavenProp);
            }
            return contextPath;
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 检测当前 PsiClass 是否含有`RestController` | `Controller` | `Path`
     *
     * @param psiClass psiClass
     * @return bool
     */
    public static boolean hasRestful(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        return SpringHelper.hasRestful(psiClass) || JaxrsHelper.hasRestful(psiClass);
    }

    /**
     * 获取properties-element的值
     *
     * @param element element
     * @param name    name
     * @return value
     */
    @NotNull
    private static String getPomFileProperties(@Nullable Element element, String name) {
        // maven element 的变量格式：${java.version}
        @Language("RegExp") final String propReg = "\\$\\{[A-Za-z0-9.:-]+}";
        if (name == null) {
            return "";
        }
        if (element == null) {
            return "";
        }
        String response = name;
        for (String nameItem : ReUtil.findAll(propReg, response, 0)) {
            Element itemElement = element.element(nameItem.substring(
                    nameItem.indexOf("{") + 1,
                    nameItem.indexOf("}")
            ));
            if (itemElement != null) {
                String itemResult = itemElement.getData().toString().trim();
                for (String itemName : ReUtil.findAll(propReg, itemResult, 0)) {
                    itemResult = itemResult.replace(itemName, getPomFileProperties(element, itemName));
                }
                response = response.replace(nameItem, itemResult);
            }
        }
        return response;
    }

    /**
     * 获取project-element的值
     *
     * @param element element
     * @param name    name
     * @return value
     */
    @NotNull
    private static String getPomFileProject(@Nullable Element element, String name) {
        // maven element 的变量格式：${project.version}
        @Language("RegExp") final String propReg = "\\$\\{[A-Za-z0-9.:-]+}";
        if (name == null) {
            return "";
        }
        if (element == null) {
            return "";
        }
        String response = name;
        for (String nameItem : ReUtil.findAll(propReg, response, 0)) {
            String elementName = nameItem.substring(
                    nameItem.indexOf("{") + 1,
                    nameItem.indexOf("}")
            );
            if (elementName.toLowerCase().startsWith("project.")) {
                elementName = elementName.substring(elementName.indexOf(".") + 1);
            }
            Element itemElement = element.element(elementName);
            if (itemElement != null) {
                String itemResult = itemElement.getData().toString().trim();
                for (String itemName : ReUtil.findAll(propReg, itemResult, 0)) {
                    itemResult = itemResult.replace(itemName, getPomFileProject(element, itemName));
                }
                response = response.replace(nameItem, itemResult);
            }
        }
        return response;
    }

    /**
     * 获取所有的Request
     *
     * @param project project
     * @return map-{key: moduleName, value: itemRequestList}
     */
    @NotNull
    public static Map<String, List<Request>> getAllRequest(@NotNull Project project) {
        return getAllRequest(project, false);
    }

    /**
     * 获取所有的Request
     *
     * @param hasEmpty 是否生成包含空Request的moduleName
     * @param project  project
     * @return map-{key: moduleName, value: itemRequestList}
     */
    @NotNull
    public static Map<String, List<Request>> getAllRequest(@NotNull Project project, boolean hasEmpty) {
        Map<String, List<Request>> map = new HashMap<>();

        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
            List<Request> requests = getAllRequestByModule(project, module);
            if (!hasEmpty && requests.isEmpty()) {
                continue;
            }
            map.put(module.getName(), requests);
        }
        return map;
    }

    /**
     * 获取选中module的所有Request
     *
     * @param project project
     * @param module  module
     * @return list
     */
    @NotNull
    public static List<Request> getAllRequestByModule(@NotNull Project project, @NotNull Module module) {
        // JAX-RS方式
        List<Request> jaxrsRequestByModule = JaxrsHelper.getJaxrsRequestByModule(project, module);
        if (!jaxrsRequestByModule.isEmpty()) {
            return jaxrsRequestByModule;
        }

        // Spring RESTFul方式
        List<Request> springRequestByModule = SpringHelper.getSpringRequestByModule(project, module);
        if (!springRequestByModule.isEmpty()) {
            return springRequestByModule;
        }
        return Collections.emptyList();
    }

    @NotNull
    public static List<Request> getCurrClassRequests(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return Collections.emptyList();
        }
        List<Request> requests;
        if (!(requests = SpringHelper.getRequests(psiClass)).isEmpty()) {
            return requests;
        }
        requests = JaxrsHelper.getCurrClassRequests(psiClass);
        return requests;
    }

    /**
     * 获取url
     *
     * @param protocol    协议
     * @param port        端口
     * @param contextPath 访问根目录名
     * @param path        路径
     * @return url
     */
    @NotNull
    public static String getRequestUrl(@NotNull String protocol, @Nullable Integer port, @Nullable String contextPath, String path) {
        StringBuilder url = new StringBuilder(protocol + "://");
        url.append("localhost");
        if (port != null) {
            url.append(":").append(port);
        }
        if (contextPath != null && !"null".equals(contextPath) && contextPath.startsWith("/")) {
            url.append(contextPath);
        }
        if (!path.startsWith("/")) {
            url.append("/");
        }
        url.append(path);
        return url.toString();
    }

    public static GlobalSearchScope getModuleScope(@NotNull Module module) {
        return getModuleScope(module, PropertiesKey.scanServiceWithLibrary(module.getProject()));
    }

    protected static GlobalSearchScope getModuleScope(@NotNull Module module, boolean hasLibrary) {
        if (hasLibrary) {
            return module.getModuleWithLibrariesScope();
        } else {
            return module.getModuleScope();
        }
    }

    /**
     * 获取属性值
     *
     * @param attributeValue Psi属性
     * @return {Object | List}
     */
    @Nullable
    public static Object getAttributeValue(JvmAnnotationAttributeValue attributeValue) {
        if (attributeValue == null) {
            return null;
        }
        if (attributeValue instanceof JvmAnnotationConstantValue) {
            return ((JvmAnnotationConstantValue) attributeValue).getConstantValue();
        } else if (attributeValue instanceof JvmAnnotationEnumFieldValue) {
            return ((JvmAnnotationEnumFieldValue) attributeValue).getFieldName();
        } else if (attributeValue instanceof JvmAnnotationArrayValue) {
            List<JvmAnnotationAttributeValue> values = ((JvmAnnotationArrayValue) attributeValue).getValues();
            List<Object> list = new ArrayList<>(values.size());
            for (JvmAnnotationAttributeValue value : values) {
                Object o = getAttributeValue(value);
                if (o != null) {
                    list.add(o);
                } else {
                    // 如果是jar包里的JvmAnnotationConstantValue则无法正常获取值
                    try {
                        Class<? extends JvmAnnotationAttributeValue> clazz = value.getClass();
                        Field myElement = clazz.getSuperclass().getDeclaredField("myElement");
                        myElement.setAccessible(true);
                        Object elObj = myElement.get(value);
                        if (elObj instanceof PsiExpression) {
                            PsiExpression expression = (PsiExpression) elObj;
                            list.add(expression.getText());
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
            return list;
        } else if (attributeValue instanceof JvmAnnotationClassValue) {
            return ((JvmAnnotationClassValue) attributeValue).getQualifiedName();
        }
        return null;
    }

    /**
     * 查找类上的指定注解（包括超类和接口）
     *
     * @param psiClass      PsiClass
     * @param qualifiedName 注解全限定名
     * @return annotation
     */
    @Nullable
    public static PsiAnnotation getClassAnnotation(@NotNull PsiClass psiClass, @NotNull String qualifiedName) {
        PsiAnnotation annotation = psiClass.getAnnotation(qualifiedName);
        if (annotation != null) {
            return annotation;
        }
        List<PsiClass> classes = new ArrayList<>();
        classes.add(psiClass.getSuperClass());
        classes.addAll(Arrays.asList(psiClass.getInterfaces()));
        for (PsiClass superPsiClass : classes) {
            if (superPsiClass == null) {
                continue;
            }
            PsiAnnotation classAnnotation = getClassAnnotation(superPsiClass, qualifiedName);
            if (classAnnotation != null) {
                return classAnnotation;
            }
        }
        return null;
    }

    /**
     * 获取方法的所有注解（包括父类）
     *
     * @param psiMethod psiMethod
     * @return annotations
     */
    @NotNull
    public static List<PsiAnnotation> getMethodAnnotations(@NotNull PsiMethod psiMethod) {
        List<PsiAnnotation> annotations = new ArrayList<>(Arrays.asList(psiMethod.getModifierList().getAnnotations()));
        for (PsiMethod superMethod : psiMethod.findSuperMethods()) {
            getMethodAnnotations(superMethod)
                    .stream()
                    // 筛选：子类中方法定义了父类中方法存在的注解时只保留最上层的注解（即实现类的方法注解
                    .filter(annotation -> !annotations.contains(annotation))
                    .forEach(annotations::add);
        }
        return annotations;
    }

    /**
     * 获取扫描到的配置文件
     *
     * @param project project
     * @param scope   scope
     * @param profile ${spring.profiles.active}
     * @return {null | PropertiesFile | YAMLFile}
     */
    @Nullable
    private static PsiFile getScanConfigurationFile(@NotNull Project project,
                                                    @NotNull GlobalSearchScope scope,
                                                    @Nullable String profile) {
        // Spring配置文件名前缀
        final String configurationPrefix = "application" + (StringUtil.isEmpty(profile) ? "" : "-" + profile);

        // 配置文件全名
        final String[] configurationFileNames = {
                // properties file
                configurationPrefix + "." + PropertiesFileType.DEFAULT_EXTENSION,
                // yaml file
                configurationPrefix + "." + YAMLFileType.DEFAULT_EXTENSION,
        };

        try {
            for (String configurationFileName : configurationFileNames) {
                PsiFile[] files = FilenameIndex.getFilesByName(project, configurationFileName, scope);

                for (PsiFile file : files) {
                    if (file instanceof PropertiesFile || file instanceof YAMLFile) {
                        // application.properties | application.yml
                        return file;
                    }
                }
            }
        } catch (NoClassDefFoundError e) {
            DumbService.getInstance(project).showDumbModeNotification(String.format(
                    "IDE is missing the corresponding package file: %s",
                    e.getMessage()
            ));
        }
        return null;
    }

    /**
     * 获取properties或yaml文件的kv值
     *
     * @param project project
     * @param scope   scope
     * @param name    name
     * @return {value | null}
     */
    @Nullable
    private static String getConfigurationValue(@NotNull Project project,
                                                @NotNull GlobalSearchScope scope,
                                                @NotNull String name) {
        PsiFile conf = getScanConfigurationFile(project, scope, null);
        if (conf == null) {
            return null;
        }
        if (conf instanceof PropertiesFile) {
            // application.properties
            PropertiesFile propertiesFile = (PropertiesFile) conf;
            String result = propertiesFile.getNamesMap().get(name);

            String active = propertiesFile.getNamesMap().get("spring.profiles.active");
            if (StringUtil.isNotEmpty(active)) {
                conf = getScanConfigurationFile(project, scope, active);
                if (conf instanceof PropertiesFile) {
                    propertiesFile = (PropertiesFile) conf;
                    String readTemp = propertiesFile.getNamesMap().get(name);
                    if (readTemp != null) {
                        result = readTemp;
                    }
                }
            }
            return result;
        } else if (conf instanceof YAMLFile) {
            // application.yml
            YAMLFile yamlFile = (YAMLFile) conf;

            // 获取application.yml文件默认profile的value
            YAMLKeyValue resultValue = YAMLUtil.getQualifiedKeyInFile(
                    yamlFile,
                    name.split("\\.")
            );

            // 获取application.yml文件默认profile定义的active
            YAMLKeyValue activeYaml = YAMLUtil.getQualifiedKeyInFile(yamlFile, "spring", "profiles", "active");
            if (activeYaml != null && StringUtil.isNotEmpty(activeYaml.getValueText())) {
                String profileName = activeYaml.getValueText();

                // 先查看application.yml中是否定义了多个profile
                List<YAMLDocument> documents = yamlFile.getDocuments();
                if (documents.size() > 1) {
                    for (int i = 1; i < documents.size(); i++) {
                        YAMLDocument yamlDocument = documents.get(i);
                        // 当前定义 profile 的名称
                        YAMLKeyValue yamlKeyValue = YAMLUtil.getQualifiedKeyInDocument(
                                yamlDocument,
                                Arrays.asList("spring", "profiles")
                        );
                        if (yamlKeyValue != null && profileName.equals(yamlKeyValue.getValueText())) {
                            YAMLKeyValue keyValue = YAMLUtil.getQualifiedKeyInDocument(
                                    yamlDocument,
                                    Arrays.asList(name.split("\\."))
                            );
                            if (keyValue != null) {
                                resultValue = keyValue;
                            }
                        }
                    }
                }
                // 内置profile未找到则寻找 classpath:application-${profileName}.yml
                if ((conf = getScanConfigurationFile(project, scope, profileName)) instanceof YAMLFile) {
                    yamlFile = (YAMLFile) conf;
                    YAMLKeyValue keyValue = YAMLUtil.getQualifiedKeyInFile(
                            yamlFile,
                            name.split("\\.")
                    );
                    if (keyValue != null) {
                        resultValue = keyValue;
                    }
                }
            }

            if (resultValue != null) {
                return resultValue.getValueText();
            }
        }
        return null;
    }

    /**
     * 获取pom文件Document
     *
     * @param module module
     * @return XmlFile
     */
    @Nullable
    private static Document getModulePomFile(@NotNull Module module) {
        String pomFileName = "pom.xml";
        try {
            File moduleFile = new File(module.getModuleFilePath());
            if (!moduleFile.exists()) {
                throw new Exception();
            }
            File pomFile = new File(moduleFile.getParent(), pomFileName);
            if (!pomFile.exists()) {
                throw new Exception();
            }
            SAXReader reader = new SAXReader();
            return reader.read(pomFile);
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * 格式化request path
     *
     * @param path path
     * @return format path
     */
    @NotNull
    @Contract(pure = true)
    public static String formatPath(@Nullable Object path) {
        if (path == null) {
            return "/";
        }
        String currPath;
        if (path instanceof String) {
            currPath = (String) path;
        } else {
            currPath = path.toString();
        }
        if (currPath.startsWith("/")) {
            return currPath;
        }
        return "/" + currPath;
    }
}
