package com.mz.jarboot.core.cmd.view;

import com.mz.jarboot.core.cmd.model.MethodNode;
import com.mz.jarboot.core.cmd.model.ThreadNode;
import com.mz.jarboot.core.cmd.model.TraceNode;
import com.mz.jarboot.core.cmd.model.ThrowNode;
import com.mz.jarboot.core.utils.DateUtils;
import com.mz.jarboot.core.utils.HtmlNodeUtils;
import com.mz.jarboot.core.utils.StringUtils;

import java.util.List;
import static java.lang.String.format;

/**
 * Term view for TraceModel
 * @author majianzheng
 */
public class TraceView implements ResultView<com.mz.jarboot.core.cmd.model.TraceModel> {
    private static final String STEP_FIRST_CHAR = "`---";
    private static final String STEP_NORMAL_CHAR = "+---";
    private static final String STEP_HAS_BOARD = "|   ";
    private static final String STEP_EMPTY_BOARD = "    ";
    private static final String TIME_UNIT = "ms";

    /** 是否输出耗时 */
    private boolean isPrintCost = true;
    private MethodNode maxCostNode;

    @Override
    public String render(com.mz.jarboot.core.cmd.model.TraceModel result) {
        return drawTree(result.getRoot());
    }

    public String drawTree(com.mz.jarboot.core.cmd.model.TraceNode root) {

        //reset status
        maxCostNode = null;
        findMaxCostNode(root);

        final StringBuilder treeSB = new StringBuilder(2048);

        recursive(0, true, "", root, (deep, isLast, prefix, node) -> {
            treeSB.append(prefix).append(isLast ? STEP_FIRST_CHAR : STEP_NORMAL_CHAR);
            renderNode(treeSB, node);
            if (!StringUtils.isBlank(node.getMark())) {
                treeSB.append(" [").append(node.getMark()).append(node.marks() > 1 ? "," + node.marks() : "").append("]");
            }
            treeSB.append("\n");
        });

        return treeSB.toString();
    }

    private void renderNode(StringBuilder sb, com.mz.jarboot.core.cmd.model.TraceNode node) {
        //render cost: [0.366865ms]
        if (isPrintCost && node instanceof MethodNode) {
            MethodNode methodNode = (MethodNode) node;

            String costStr = renderCost(methodNode);
            if (node == maxCostNode) {
                // the node with max cost will be highlighted
                sb.append(HtmlNodeUtils.red(costStr));
            } else {
                sb.append(costStr);
            }
        }

        //render method name
        if (node instanceof MethodNode) {
            MethodNode methodNode = (MethodNode) node;
            //clazz.getName() + ":" + method.getName() + "()"
            sb.append(methodNode.getClassName()).append(":").append(methodNode.getMethodName()).append("()");
            // #lineNumber
            if (methodNode.getLineNumber()!= -1) {
                sb.append(" #").append(methodNode.getLineNumber());
            }
        } else if (node instanceof ThreadNode) {
            //render thread info
            ThreadNode threadNode = (ThreadNode) node;
            //ts=2020-04-29 10:34:00;thread_name=main;id=1;is_daemon=false;priority=5;TCCL=sun.misc.Launcher$AppClassLoader@18b4aac2
            sb.append(format("ts=%s;thread_name=%s;id=%s;is_daemon=%s;priority=%d;TCCL=%s",
                    DateUtils.formatDate(threadNode.getTimestamp()),
                    threadNode.getThreadName(),
                    Long.toHexString(threadNode.getThreadId()),
                    threadNode.isDaemon(),
                    threadNode.getPriority(),
                    threadNode.getClassloader()));

            //trace_id
            if (threadNode.getTraceId() != null) {
                sb.append(";trace_id="+threadNode.getTraceId());
            }
            if (threadNode.getRpcId() != null) {
                sb.append(";rpc_id="+threadNode.getRpcId());
            }
        } else if (node instanceof ThrowNode) {
            ThrowNode throwNode = (ThrowNode) node;
            sb.append("throw:").append(throwNode.getException())
                    .append(" #").append(throwNode.getLineNumber())
                    .append(" [").append(throwNode.getMessage()).append("]");

        } else {
            throw new UnsupportedOperationException("unknown trace node: " + node.getClass());
        }
    }

    private String renderCost(MethodNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.getTimes() <= 1) {
            sb.append("[").append(nanoToMillis(node.getCost())).append(TIME_UNIT).append("] ");
        } else {
            sb.append("[min=").append(nanoToMillis(node.getMinCost())).append(TIME_UNIT).append(",max=")
                    .append(nanoToMillis(node.getMaxCost())).append(TIME_UNIT).append(",total=")
                    .append(nanoToMillis(node.getTotalCost())).append(TIME_UNIT).append(",count=")
                    .append(node.getTimes()).append("] ");
        }
        return sb.toString();
    }

    /**
     * 递归遍历
     */
    private void recursive(int deep, boolean isLast, String prefix,
                           com.mz.jarboot.core.cmd.model.TraceNode node, Callback callback) {
        callback.callback(deep, isLast, prefix, node);
        if (!isLeaf(node)) {
            List<TraceNode> children = node.getChildren();
            if (children == null) {
                return;
            }
            final int size = children.size();
            for (int index = 0; index < size; index++) {
                final boolean isLastFlag = index == size - 1;
                final String currentPrefix = isLast ? prefix + STEP_EMPTY_BOARD : prefix + STEP_HAS_BOARD;
                recursive(
                        deep + 1,
                        isLastFlag,
                        currentPrefix,
                        children.get(index),
                        callback
                );
            }
        }
    }

    /**
     * 查找耗时最大的节点，便于后续高亮展示
     * @param node
     */
    private void findMaxCostNode(com.mz.jarboot.core.cmd.model.TraceNode node) {
        if (node instanceof MethodNode && !isRoot(node) && !isRoot(node.parent())) {
            MethodNode aNode = (MethodNode) node;
            if (maxCostNode == null || maxCostNode.getTotalCost() < aNode.getTotalCost()) {
                maxCostNode = aNode;
            }
        }
        if (!isLeaf(node)) {
            List<com.mz.jarboot.core.cmd.model.TraceNode> children = node.getChildren();
            if (children != null) {
                for (com.mz.jarboot.core.cmd.model.TraceNode n: children) {
                    findMaxCostNode(n);
                }
            }
        }
    }

    private boolean isRoot(TraceNode node) {
        return node.parent() == null;
    }

    private boolean isLeaf(TraceNode node) {
        List<TraceNode> children = node.getChildren();
        return children == null || children.isEmpty();
    }


    /**
     * convert nano-seconds to milli-seconds
     */
    double nanoToMillis(long nanoSeconds) {
        return nanoSeconds / 1000000.0;
    }

    /**
     * 遍历回调接口
     */
    private interface Callback {

        /**
         * 回调方法
         * @param deep 深度
         * @param isLast 是否最后
         * @param prefix prefix
         * @param node node
         */
        void callback(int deep, boolean isLast, String prefix, TraceNode node);
    }
}
