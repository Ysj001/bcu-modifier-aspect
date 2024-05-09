package com.ysj.lib.bcu.modifier.aspect.processor

import com.ysj.lib.bcu.modifier.aspect.PREFIX_PROXY_METHOD
import com.ysj.lib.bcu.modifier.aspect.PointcutBean
import com.ysj.lib.bcu.modifier.aspect.api.JoinPoint
import com.ysj.lib.bcu.modifier.aspect.api.POSITION_CALL
import com.ysj.lib.bcu.modifier.aspect.callingPointDesc
import com.ysj.lib.bcu.modifier.aspect.callingPointInternalName
import com.ysj.lib.bcu.modifier.aspect.isConstructor
import com.ysj.lib.bcu.modifier.aspect.joinPointDesc
import com.ysj.lib.bytecodeutil.plugin.api.BCUKeep
import com.ysj.lib.bytecodeutil.plugin.api.CLASS_TYPE
import com.ysj.lib.bytecodeutil.plugin.api.cast
import com.ysj.lib.bytecodeutil.plugin.api.classInsnNode
import com.ysj.lib.bytecodeutil.plugin.api.firstNode
import com.ysj.lib.bytecodeutil.plugin.api.isStatic
import com.ysj.lib.bytecodeutil.plugin.api.logger.YLogger
import com.ysj.lib.bytecodeutil.plugin.api.md5
import com.ysj.lib.bytecodeutil.plugin.api.opcodeLoad
import com.ysj.lib.bytecodeutil.plugin.api.wrapperType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 方法调用代理处理器
 *
 * @author Ysj
 * Create time: 2021/8/15
 */
class MethodProxyProcessor(
    private val allClassNode: Map<String, ClassNode>,
    globalCache: MutableMap<String, Any?>,
) : BaseMethodProcessor(globalCache) {

    private val logger = YLogger.getLogger(javaClass)

    // 记录代理替换的节点。key：代理的节点 value：源节点
    private val recordProxyNode = ConcurrentHashMap<MethodInsnNode, MethodInsnNode>()

    private val arrayType = Type.getType(Array::class.java)
    private val anyType = Type.getType(Any::class.java)
    private val bcuKeepDesc = Type.getType(BCUKeep::class.java).descriptor

    fun process(pointcutBean: PointcutBean, classNode: ClassNode, methodNode: MethodNode) {
        if (pointcutBean.position != POSITION_CALL) return
        val firstNode = methodNode.firstNode ?: return
        val insnList = methodNode.instructions
        val insnNodes = insnList.toArray()
        insnNodes.forEach node@{ node ->
            if (node !is MethodInsnNode) return@node
            val proxy = recordProxyNode[node]
            val realNode = proxy ?: node
            pointcutBean.takeIf {
                isAnnotationTarget(it, realNode) || Pattern.matches(it.target, realNode.owner)
                    && Pattern.matches(it.funName, realNode.name)
                    && Pattern.matches(it.funDesc, realNode.desc)
            } ?: return@node
            if (node.isConstructor) {
                if (methodNode.isConstructor && node.owner == classNode.name) {
                    // 不去代理构造方法中自己调用的 this()
                    return@node
                }
                // 构造方法需要移除 NEW 和 DUP 指令
                var preNode = node.previous
                while (preNode != null) {
                    if (preNode.opcode == Opcodes.NEW
                        && preNode is TypeInsnNode
                        && preNode.desc == node.owner) {
                        insnList.remove(preNode.next)
                        insnList.remove(preNode)
                        break
                    }
                    preNode = preNode.previous
                }
            }
            // 切面方法的参数
            val aspectFunArgs = pointcutBean.aspectFunArgs
            val hasJoinPoint = aspectFunArgs.indexOfFirst { it.className == JoinPoint::class.java.name } >= 0
            if (hasJoinPoint && !firstNode.beforeIsStoredJoinPoint) {
                insnList.insertBefore(firstNode, storeJoinPoint(classNode, methodNode))
            }
            val proxyMethod = classNode.generateProxyMethod(pointcutBean, node, hasJoinPoint)
            val proxyNode = MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, proxyMethod.name, proxyMethod.desc, false)
            // 把源调用替换成代理调用
            insnList.insertBefore(node, proxyNode)
            insnList.remove(node)
            if (proxy != null) recordProxyNode.remove(proxy)
            else node.addBCUKeep()
            recordProxyNode[proxyNode] = realNode
            // 插入参数
            if (hasJoinPoint) insnList.insertBefore(proxyNode, getJoinPoint(classNode, methodNode))
            logger.info("Method Call 插入 --> ${classNode.name}#${methodNode.name}${methodNode.desc}")
        }
        if (firstNode.beforeIsStoredJoinPoint && !isRemovedJoinPoint(classNode, methodNode)) {
            for (insnNode in insnList) {
                if (insnNode.opcode !in Opcodes.IRETURN..Opcodes.RETURN) continue
                insnList.insertBefore(insnNode, removeJoinPoint(classNode, methodNode))
            }
            cacheRemovedJoinPoint(classNode, methodNode)
        }
    }

    private fun MethodInsnNode.addBCUKeep() {
        val classNode = allClassNode[owner] ?: return
        synchronized(classNode.methods) {
            classNode.methods.find { it.name == name && it.desc == desc }?.addBCUKeep()
        }
    }

    /**
     * 使用 [BCUKeep] 注解标记方法，便于混淆后保留
     */
    private fun MethodNode.addBCUKeep() {
        var annotations = invisibleAnnotations
        if (annotations == null) {
            annotations = ArrayList()
            invisibleAnnotations = annotations
        }
        if (annotations.find { it.desc == bcuKeepDesc } != null) return
        annotations.add(AnnotationNode(bcuKeepDesc))
    }

    /**
     * 在指定类中生成代理方法
     * ```
     * static {returnType} {proxyMethodName}(caller, ...args, JoinPoint) {
     *     Class[] argTypes = new Class[]{...argTypes};
     *     CallingPoint callingPoint = CallingPoint.newInstance(caller, isStatic, funName, argTypes, new Object[]{...args});
     *     {returnType} result = ({returnType}){AspectClass}.instance.{aspectFun}(JoinPoint, callingPoint);
     *     callingPoint.orgCallingPoint = CallingPoint.newInstance(orgCallingPointParams);
     *     callingPoint.release();
     *     return result;
     * }
     * ```
     */
    private fun ClassNode.generateProxyMethod(pointcut: PointcutBean, calling: MethodInsnNode, hasJoinPoint: Boolean): MethodNode {
        val proxyName = "${calling.owner}${calling.name}${calling.desc}".md5()
        synchronized(methods) {
            var find: MethodNode? = null
            // 代理方法都添加到了 methods 的后面，从后面查比较快
            for (i in methods.lastIndex downTo 0) {
                val method = methods[i]
                // 代理方法都有前缀，没有前缀说明没生成过直接 break
                if (!method.name.startsWith(PREFIX_PROXY_METHOD)) break
                if (method.name.endsWith(proxyName)) {
                    find = method
                    break
                }
            }
            if (find != null) return find
        }
        val callerType = Type.getObjectType(calling.owner)
        val callerDesc = when {
            calling.isStatic -> ""
            calling.isConstructor -> ""
            else -> callerType.descriptor
        }
        val args = Type.getArgumentTypes(calling.desc)
        val argsDesc = args.map { it.descriptor }.toTypedArray().contentToString().run {
            substring(1 until lastIndex).replace(", ", "")
        }
        val returnType = when {
            calling.isConstructor -> callerType
            else -> Type.getReturnType(calling.desc)
        }
        val joinPointDesc = if (hasJoinPoint) joinPointDesc else ""
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            "$PREFIX_PROXY_METHOD$proxyName",
            "($callerDesc$argsDesc$joinPointDesc)${returnType.descriptor}",
            null,
            null
        )
        method.instructions.apply {
            val callerStoreIndex = when {
                calling.isStatic -> -1
                calling.isConstructor -> -1
                else -> 0
            }
            val argsLastStoreIndex = callerStoreIndex + args.size
            val argTypesStoreIndex = argsLastStoreIndex + if (hasJoinPoint) 2 else 1
            // Class[] argTypes = new Class[]{...argTypes}
            add(args.arrayWrap(CLASS_TYPE) {
                add(it.classInsnNode)
            })
            add(VarInsnNode(Opcodes.ASTORE, argTypesStoreIndex))
            // CallingPoint callingPoint = CallingPoint.newInstance(xxx);
            val callingPointStoreIndex = argTypesStoreIndex + 1
            createCallingPoint(calling, callerType, args, argTypesStoreIndex)
            add(VarInsnNode(Opcodes.ASTORE, callingPointStoreIndex))
            // callingPoint.orgCallingPoint = CallingPoint.newInstance(orgCallingPointParams);
            recordProxyNode[calling]?.also {
                add(VarInsnNode(Opcodes.ALOAD, callingPointStoreIndex))
                createCallingPoint(it, Type.getObjectType(it.owner), args.sliceArray(1..args.lastIndex), argTypesStoreIndex)
                add(FieldInsnNode(
                    Opcodes.PUTFIELD,
                    callingPointInternalName,
                    "orgCallingPoint",
                    callingPointDesc
                ))
            }
            // {AspectClass}.instance
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                pointcut.aspectClassName,
                com.ysj.lib.bcu.modifier.aspect.ASPECT_CLASS_INSTANCE,
                Type.getObjectType(pointcut.aspectClassName).descriptor
            ))
            // joinPoint
            if (hasJoinPoint) {
                add(VarInsnNode(Opcodes.ALOAD, argsLastStoreIndex + 1))
            }
            // callingPoint
            add(VarInsnNode(Opcodes.ALOAD, callingPointStoreIndex))
            val returnIndex = callingPointStoreIndex + 1
            // {aspectFun}(JoinPoint, callingPoint)
            add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                pointcut.aspectClassName,
                pointcut.aspectFunName,
                pointcut.aspectFunDesc,
                false
            ))
            add(cast(Type.getReturnType(pointcut.aspectFunDesc), returnType))
            if (returnType.sort != Type.METHOD && returnType.sort != Type.VOID) {
                add(VarInsnNode(returnType.getOpcode(Opcodes.ISTORE), returnIndex))
            }
            // callingPoint.release()
            add(VarInsnNode(Opcodes.ALOAD, callingPointStoreIndex))
            add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                callingPointInternalName,
                "release",
                "()V",
                false
            ))
            if (returnType.sort != Type.METHOD && returnType.sort != Type.VOID) {
                add(VarInsnNode(returnType.getOpcode(Opcodes.ILOAD), returnIndex))
            }
            add(InsnNode(returnType.getOpcode(Opcodes.IRETURN)))
        }
        synchronized(methods) {
            method.addBCUKeep()
            methods.add(method)
        }
        return method
    }

    private fun InsnList.createCallingPoint(calling: MethodInsnNode, callerType: Type, args: Array<Type>, argTypesIndex: Int) {
        // caller
        when {
            calling.isStatic -> add(callerType.classInsnNode)
            calling.isConstructor -> {
                add(callerType.classInsnNode)
                add(VarInsnNode(Opcodes.ALOAD, argTypesIndex))
                add(MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getConstructor",
                    "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;",
                    false
                ))
            }
            else -> add(VarInsnNode(Opcodes.ALOAD, 0))
        }
        // isStatic
        add(InsnNode(if (calling.isStatic) Opcodes.ICONST_1 else Opcodes.ICONST_0))
        // funName
        add(LdcInsnNode(if (calling.isConstructor) "newInstance" else calling.name))
        if (calling.isConstructor) {
            // argTypes
            add(arrayOf(arrayType).arrayWrap(CLASS_TYPE) {
                add(it.classInsnNode)
            })
            // new Object[]{new Object[]{...args}}
            var startLoadIndex = when {
                calling.isStatic -> 0
                calling.isConstructor -> 0
                else -> 1
            }
            add(arrayOf(arrayType).arrayWrap(anyType) { _ ->
                add(args.arrayWrap(anyType) { t2 ->
                    add(VarInsnNode(t2.opcodeLoad(), startLoadIndex))
                    // 计算当前参数的索引
                    startLoadIndex += t2.size
                    if (t2.needWrap()) {
                        add(t2.wrapNode())
                    }
                })
            })
        } else {
            // argTypes
            add(VarInsnNode(Opcodes.ALOAD, argTypesIndex))
            // new Object[]{...args}
            var startLoadIndex = when {
                calling.isStatic -> 0
                calling.isConstructor -> 0
                else -> 1
            }
            add(args.arrayWrap(anyType) {
                // 计算当前参数的索引
                add(VarInsnNode(it.opcodeLoad(), startLoadIndex))
                startLoadIndex += it.size
                if (it.needWrap()) {
                    add(it.wrapNode())
                }
            })
        }
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            callingPointInternalName,
            "newInstance",
            "(Ljava/lang/Object;ZLjava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)$callingPointDesc",
            false
        ))
    }

    private fun Array<Type>.arrayWrap(arrType: Type, block: InsnList.(Type) -> Unit) = InsnList().also { list ->
        list.add(IntInsnNode(Opcodes.BIPUSH, size))
        list.add(TypeInsnNode(Opcodes.ANEWARRAY, arrType.internalName))
        for (index in indices) {
            val type = get(index)
            list.add(InsnNode(Opcodes.DUP))
            list.add(IntInsnNode(Opcodes.BIPUSH, index))
            list.block(type)
            list.add(InsnNode(Opcodes.AASTORE))
        }
    }

    private fun Type.needWrap(): Boolean {
        return sort in Type.BOOLEAN..Type.DOUBLE
    }

    private fun Type.wrapNode(): MethodInsnNode {
        // primitive types must be boxed
        val wrapperType = wrapperType
        return MethodInsnNode(
            Opcodes.INVOKESTATIC,
            wrapperType.internalName,
            "valueOf",
            "(${descriptor})${wrapperType.descriptor}",
            false
        )
    }

    private fun isAnnotationTarget(pointcutBean: PointcutBean, node: MethodInsnNode): Boolean {
        if (pointcutBean.targetType != PointcutBean.TARGET_ANNOTATION) return false
        val classNode = allClassNode[node.owner] ?: return false
        val predicate: (AnnotationNode) -> Boolean = { Pattern.matches(pointcutBean.target, it.desc) }
        return synchronized(classNode.methods) {
            classNode.methods.find {
                it.name == node.name && it.desc == node.desc &&
                    (it.visibleAnnotations?.find(predicate) != null || it.invisibleAnnotations?.find(predicate) != null)
            } != null
        }
    }
}