package core.actions.copy;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author ZhangYuanSheng
 * @version 1.0
 */
public class CopyApiAction extends AnAction implements CopyOption {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        copyPath(e, false);
    }
}
