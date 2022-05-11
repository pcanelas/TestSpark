package nl.tudelft.ewi.se.ciselab.testgenie.actions

import nl.tudelft.ewi.se.ciselab.testgenie.evosuite.ResultWatcher
import nl.tudelft.ewi.se.ciselab.testgenie.evosuite.Runner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.map2Array

/**
 * This class generates tests for a method.
 */
class GenerateTestsActionMethod : AnAction() {
    private val log = Logger.getInstance(this.javaClass)

    /**
     * Performs test generation for a method when the action is invoked.
     *
     * @param e an action event that contains useful information
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return

        val psiFile: PsiFile = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        val caret: Caret = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return

        val psiMethod: PsiMethod = GenerateTestsUtils.getSurroundingMethod(psiFile, caret) ?: return
        val containingClass: PsiClass = psiMethod.containingClass ?: return

        val method = psiMethod.name
        val signature: Array<String> =
            psiMethod.getSignature(PsiSubstitutor.EMPTY).parameterTypes.map2Array { it.canonicalText }
        val returnType: String = psiMethod.returnType?.canonicalText ?: "void"
        val classFQN = containingClass.qualifiedName ?: return

        val projectPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path
        val projectClassPath = "$projectPath/target/classes/"

        log.info("Generating tests for project $projectPath with classpath $projectClassPath")

        log.info("Selected method is $classFQN::$method${signature.contentToString()}$returnType")

        //TODO: remove this line
        Messages.showInfoMessage(
            "Selected method is $classFQN::$method${
                signature.contentToString().replace("[", "(").replace("]", ")")
            }$returnType",
            "Selected"
        )

        val resultPath = Runner(projectPath, projectClassPath, classFQN).forMethod(method).runEvoSuite()

        AppExecutorUtil.getAppScheduledExecutorService().execute(ResultWatcher(project, resultPath))
    }

    /**
     * Makes the action visible only if a method has been selected.
     * It also updates the action name depending on which method has been selected.
     *
     * @param e an action event that contains useful information
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = false

        val caret: Caret = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return
        val psiFile: PsiFile = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return

        val psiMethod: PsiMethod = GenerateTestsUtils.getSurroundingMethod(psiFile, caret) ?: return

        e.presentation.isEnabledAndVisible = true
        e.presentation.text = "Generate Tests For ${GenerateTestsUtils.getMethodDisplayName(psiMethod)}"
    }
}