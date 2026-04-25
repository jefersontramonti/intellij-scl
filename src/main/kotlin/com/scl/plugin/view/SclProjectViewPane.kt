package com.scl.plugin.view

import com.intellij.ide.impl.ProjectViewSelectInPaneTarget
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.scl.plugin.SclIcons
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclTopLevelDecl
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class SclProjectViewPane(project: Project) : AbstractProjectViewPane(project), DumbAware {

    companion object {
        const val ID = "SclView"
    }

    private lateinit var scrollPane: JScrollPane

    override fun getId(): String = ID
    override fun getTitle(): String = "SCL"
    override fun getIcon(): Icon = SclIcons.FUNCTION_BLOCK
    override fun getWeight(): Int = 10

    override fun createComponent(): JComponent {
        val root = DefaultMutableTreeNode("SCL")
        myTree = DnDAwareTree(DefaultTreeModel(root))
        myTree.isRootVisible = false
        myTree.showsRootHandles = true
        myTree.cellRenderer = SclTreeCellRenderer()

        myProject.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { it.file?.extension?.lowercase() == "scl" }) {
                        scheduleRefresh()
                    }
                }
            },
        )

        scheduleRefresh()
        scrollPane = JScrollPane(myTree)
        return scrollPane
    }

    private fun scheduleRefresh() {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runReadAction { rebuildTree() }
        }
    }

    private fun rebuildTree() {
        val fbGroup  = DefaultMutableTreeNode(GroupNode("Function Blocks",     SclIcons.FUNCTION_BLOCK))
        val fcGroup  = DefaultMutableTreeNode(GroupNode("Functions",           SclIcons.FUNCTION))
        val obGroup  = DefaultMutableTreeNode(GroupNode("Organization Blocks", SclIcons.ORG_BLOCK))
        val udtGroup = DefaultMutableTreeNode(GroupNode("Data Types",          SclIcons.UDT))

        val files = FileTypeIndex.getFiles(
            SclFileType, GlobalSearchScope.projectScope(myProject),
        ).sortedBy { it.name }

        val psiManager = PsiManager.getInstance(myProject)
        for (vf in files) {
            val psiFile = psiManager.findFile(vf) ?: continue
            val first = psiFile.children.filterIsInstance<SclTopLevelDecl>().firstOrNull()
            val node = DefaultMutableTreeNode(FileNode(vf))
            when {
                first?.functionBlockDecl != null -> fbGroup.add(node)
                first?.functionDecl != null      -> fcGroup.add(node)
                first?.orgBlockDecl != null      -> obGroup.add(node)
                first?.typeDecl != null          -> udtGroup.add(node)
                else                             -> fbGroup.add(node)
            }
        }

        val newRoot = DefaultMutableTreeNode("SCL")
        for (group in listOf(fbGroup, fcGroup, obGroup, udtGroup)) {
            if (group.childCount > 0) newRoot.add(group)
        }

        SwingUtilities.invokeLater {
            (myTree.model as DefaultTreeModel).setRoot(newRoot)
        }
    }

    override fun createSelectInTarget() =
        ProjectViewSelectInPaneTarget(myProject, this, /* dumbAware = */ true)

    override fun getData(dataId: String): Any? = null

    override fun updateFromRoot(restoreExpandedPaths: Boolean): ActionCallback {
        scheduleRefresh()
        return ActionCallback.DONE
    }

    override fun select(element: Any?, file: VirtualFile?, requestFocus: Boolean) {
        file ?: return
        val root = myTree.model.root as? DefaultMutableTreeNode ?: return
        for (gi in 0 until root.childCount) {
            val group = root.getChildAt(gi) as? DefaultMutableTreeNode ?: continue
            for (ci in 0 until group.childCount) {
                val child = group.getChildAt(ci) as? DefaultMutableTreeNode ?: continue
                if ((child.userObject as? FileNode)?.vf == file) {
                    val path = TreePath(child.path)
                    myTree.selectionPath = path
                    myTree.scrollPathToVisible(path)
                    if (requestFocus) myTree.requestFocus()
                    return
                }
            }
        }
    }

    override fun getComponentToFocus(): JComponent = myTree ?: JPanel()
}

// ─────────────────────────────────────────────────────────────────────────────

data class GroupNode(val title: String, val icon: Icon)
data class FileNode(val vf: VirtualFile)

class SclTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree, value: Any, selected: Boolean,
        expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean,
    ): Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        when (val obj = (value as? DefaultMutableTreeNode)?.userObject) {
            is GroupNode -> { text = obj.title; icon = obj.icon }
            is FileNode  -> { text = obj.vf.name; icon = SclFileType.icon }
        }
        return this
    }
}
