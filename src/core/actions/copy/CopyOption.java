package core.actions.copy;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import core.actions.EditorOption;
import core.beans.Request;
import core.service.Notify;
import core.utils.RestUtil;
import core.utils.SystemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ZhangYuanSheng
 * @version 1.0
 */
public interface CopyOption extends EditorOption {

    PsiMethod[] PSI_METHODS = new PsiMethod[1];

    /**
     * 检测当前 PsiClass 是否含有`RestController` | `Controller` | `Path`
     *
     * @param psiMethod psiMethod
     * @return bool
     */
    default boolean hasRestful(@Nullable PsiMethod psiMethod) {
        if (psiMethod == null) {
            return false;
        }
        return RestUtil.hasRestful(psiMethod.getContainingClass());
    }

    /**
     * 获取当前选择的PsiMethod
     *
     * @param e AnActionEvent
     * @return PsiMethod
     */
    @Nullable
    default PsiMethod getPsiMethod(@NotNull AnActionEvent e) {
        PsiElement currentEditorElement = EditorOption.getCurrentEditorElement(e);
        if (currentEditorElement == null) {
            return null;
        }
        // 如果右键处为当前方法其中的 注解末尾 或 方法体中
        PsiElement editorElementContext = currentEditorElement.getContext();
        if (editorElementContext != null) {
            if (editorElementContext instanceof PsiClass) {
                return null;
            }
            if (editorElementContext instanceof PsiMethod) {
                return ((PsiMethod) editorElementContext);
            }
        }
        return null;
    }

    /**
     * 获取Request Path
     *
     * @param psiMethod psiMethod
     * @return paths
     */
    @NotNull
    default List<String> getRequestPath(@NotNull PsiMethod psiMethod) {
        Set<String> paths = new HashSet<>();
        for (Request request : RestUtil.getCurrClassRequests(psiMethod.getContainingClass())) {
            if (request.getPsiMethod().equals(psiMethod)) {
                paths.add(request.getPath());
            }
        }
        return new ArrayList<>(paths);
    }

    /**
     * 是否包含PsiMethod
     *
     * @param e AnActionEvent
     * @return bool
     */
    default boolean withPsiMethod(@NotNull AnActionEvent e) {
        if (e.getProject() == null) {
            return false;
        }
        PSI_METHODS[0] = getPsiMethod(e);
        return hasRestful(PSI_METHODS[0]);
    }

    /**
     * 复制
     *
     * @param e    AnActionEvent
     * @param full 全量
     */
    default void copyPath(@NotNull AnActionEvent e, boolean full) {
        Project project = e.getData(LangDataKeys.PROJECT);
        PsiMethod psiMethod = PSI_METHODS[0];
        if (project == null || psiMethod == null) {
            return;
        }
        List<String> requests = getRequestPath(psiMethod);
        if (requests.isEmpty()) {
            return;
        }
        StringBuilder paths = new StringBuilder();
        String contextPath = RestUtil.scanContextPath(project, psiMethod.getResolveScope());
        if (!full) {
            for (int i = 0; i < requests.size(); i++) {
                if (i > 0) {
                    paths.append("\n");
                }
                paths.append(contextPath == null || "null".equals(contextPath) ? "" : contextPath)
                        .append(requests.get(i));
            }
        } else {
            GlobalSearchScope scope = psiMethod.getResolveScope();
            String protocol = RestUtil.scanListenerProtocol(project, scope);
            int port = RestUtil.scanListenerPort(project, scope);
            for (int i = 0; i < requests.size(); i++) {
                if (i > 0) {
                    paths.append("\n");
                }
                paths.append(RestUtil.getRequestUrl(
                        protocol,
                        port,
                        contextPath,
                        requests.get(i)
                ));
            }
        }
        SystemUtil.setClipboardString(paths.toString());
        Notify.getInstance(project).info("Copy path success.");
    }
}
