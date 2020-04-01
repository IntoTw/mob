package cn.intotw.mob;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.sun.tools.javac.util.List.from;
import static com.sun.tools.javac.util.List.nil;

@SupportedAnnotationTypes("cn.intotw.*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@Deprecated
public class MobCloudProviderProcessor extends AbstractProcessor {
    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    private JCTree.JCClassDecl templateClass;
    Map<String, JCTree.JCAssign> sourceAnnotationValue = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return handleProvider(roundEnv);
    }

    private boolean handleProvider(RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(MobCloudProvider.class);
        //获取模板类
        //构建内部类
        set.forEach(element -> {
            addProviderImport(element);
            buildSourceAnnotationValue(element);
            JCTree jcTree = trees.getTree(element);
            final java.util.List<JCTree.JCMethodDecl> methodDecls = new ArrayList<>();
            //获取需要处理的方法
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
                    methodDecls.add(jcMethodDecl);
                    super.visitMethodDef(jcMethodDecl);
                }
            });
            //根据方法自动生成controller
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    JCTree.JCClassDecl jcClassDecl1 = buildInnerClass(jcClassDecl,templateClass, methodDecls);
                    jcClassDecl.defs = jcClassDecl.defs.append(jcClassDecl1);
                }
            });

        });
        set.forEach(element -> {
            JCTree jcTree = trees.getTree(element);
            printLog("result :{}", jcTree);
        });

        return true;
    }

    private JCTree.JCClassDecl buildInnerClass(JCTree.JCClassDecl sourceClassDecl,JCTree.JCClassDecl templateClassDecl, java.util.List<JCTree.JCMethodDecl> methodDecls) {
        java.util.List<JCTree.JCVariableDecl> jcVariableDeclList = buildInnerClassVar(sourceClassDecl);
        String lowerClassName=sourceClassDecl.getSimpleName().toString();
        lowerClassName=lowerClassName.substring(0,1).toLowerCase().concat(lowerClassName.substring(1));
        java.util.List<JCTree.JCMethodDecl> jcMethodDecls = buildInnerClassMethods(methodDecls,
                lowerClassName);
        java.util.List<JCTree> jcTrees=new ArrayList<>();
        jcTrees.addAll(jcVariableDeclList);
        jcTrees.addAll(jcMethodDecls);
        JCTree.JCClassDecl targetClassDecl = treeMaker.ClassDef(
                buildInnerClassAnnotation(),
                names.fromString(sourceClassDecl.name.toString().concat("InnerController")),
                List.nil(),
                null,
                List.nil(),
                List.from(jcTrees));
        return targetClassDecl;
    }

    private java.util.List<JCTree.JCVariableDecl> buildInnerClassVar(JCTree.JCClassDecl jcClassDecl) {
        String parentClassName=jcClassDecl.getSimpleName().toString();
        printLog("simpleClassName:{}",parentClassName);
        java.util.List<JCTree.JCVariableDecl> jcVariableDeclList=new ArrayList<>();
        java.util.List<JCTree.JCAnnotation> jcAnnotations=new ArrayList<>();
        JCTree.JCAnnotation jcAnnotation=makeAnnotation(PackageSupportEnum.Autowired.toString()
        ,List.nil());
        jcAnnotations.add(jcAnnotation);
        JCTree.JCVariableDecl jcVariableDecl = treeMaker.VarDef(treeMaker.Modifiers(1, from(jcAnnotations)),
                names.fromString(parentClassName.substring(0, 1).toLowerCase().concat(parentClassName.substring(1))),
                treeMaker.Ident(names.fromString(parentClassName)),
                null);
        jcVariableDeclList.add(jcVariableDecl);
        return jcVariableDeclList;
    }

    private JCTree.JCModifiers buildInnerClassAnnotation() {
        JCTree.JCExpression jcAssign=makeArg("value",sourceAnnotationValue.get("feignClientPrefix").rhs.toString().replace("\"",""));
        JCTree.JCAnnotation jcAnnotation=makeAnnotation(PackageSupportEnum.RequestMapping.toString(),
             List.of(jcAssign)
        );
        JCTree.JCAnnotation restController=makeAnnotation(PackageSupportEnum.RestController.toString(),List.nil());
        JCTree.JCModifiers mods=treeMaker.Modifiers(Flags.PUBLIC|Flags.STATIC,List.of(jcAnnotation).append(restController));
        return mods;
    }

    private java.util.List<JCTree.JCMethodDecl> buildInnerClassMethods(java.util.List<JCTree.JCMethodDecl> methodDecls,String serviceName) {
        java.util.List<JCTree.JCMethodDecl> target = new ArrayList<>();
        methodDecls.forEach(e -> {
            if (!e.name.contentEquals("<init>")) {
                java.util.List<JCTree.JCVariableDecl> targetParams=new ArrayList<>();
                e.params.forEach(param->{
                    JCTree.JCVariableDecl newParam=treeMaker.VarDef(
                            (JCTree.JCModifiers) param.mods.clone(),
                            param.name,
                            param.vartype,
                            param.init
                    );
                    printLog("copy of param:{}",newParam);
                    targetParams.add(newParam);
                });
                JCTree.JCMethodDecl methodDecl = treeMaker.MethodDef(
                        (JCTree.JCModifiers) e.mods.clone(),
                        e.name,
                        (JCTree.JCExpression) e.restype.clone(),
                        e.typarams,
                        e.recvparam,
                        List.from(targetParams),
                        e.thrown,
                        treeMaker.Block(0L,List.nil()),
                        e.defaultValue
                );
                target.add(methodDecl);
            }
        });
        target.forEach(e -> {
            e.params.get(0).mods.annotations = e.params.get(0).mods.annotations.append(makeAnnotation(PackageSupportEnum.RequestBody.toString(), nil()));
            printLog("sourceMethods: {}", e);
            //value
            JCTree.JCExpression jcAssign=makeArg("value","/"+e.name.toString());

            JCTree.JCAnnotation jcAnnotation = makeAnnotation(
                    PackageSupportEnum.PostMapping.toString(),
                    List.of(jcAssign)
            );
            printLog("annotation: {}", jcAnnotation);
            e.mods.annotations = e.mods.annotations.append(jcAnnotation);
            JCTree.JCExpressionStatement exec = getMethodInvocationStat(serviceName, e.name.toString(), e.params);
            JCTree.JCReturn jcReturn=treeMaker.Return(exec.getExpression());
            e.body.stats = e.body.stats.append(jcReturn);
        });
        return List.from(target);
    }

    private JCTree.JCExpressionStatement getMethodInvocationStat(String invokeFrom, String invokeMethod, List<JCTree.JCVariableDecl> args) {
        java.util.List<JCTree.JCIdent> params = new ArrayList<>();
        args.forEach(e -> {
            params.add(treeMaker.Ident(e.name));
        });
        JCTree.JCIdent invocationFrom = treeMaker.Ident(names.fromString(invokeFrom));
        JCTree.JCFieldAccess jcFieldAccess1 = treeMaker.Select(invocationFrom, names.fromString(invokeMethod));
        JCTree.JCMethodInvocation apply = treeMaker.Apply(nil(), jcFieldAccess1, List.from(params));
        JCTree.JCExpressionStatement exec = treeMaker.Exec(apply);
        printLog("method invoke:{}", exec);
        return exec;
    }


    private void buildSourceAnnotationValue(Element element) {
        JCTree jcTree = trees.getTree(element);
        //获取源注解的参数
        jcTree.accept(new TreeTranslator() {
            @Override
            public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                JCTree.JCIdent jcIdent = (JCTree.JCIdent) jcAnnotation.getAnnotationType();
                if (jcIdent.name.contentEquals("MobCloudProvider")) {
                    printLog("class Annotation arg process:{}", jcAnnotation.toString());
                    jcAnnotation.args.forEach(e -> {
                        JCTree.JCAssign jcAssign = (JCTree.JCAssign) e;
                        JCTree.JCIdent value = treeMaker.Ident(names.fromString("value"));
                        JCTree.JCAssign targetArg = treeMaker.Assign(value, jcAssign.rhs);
                        sourceAnnotationValue.put(jcAssign.lhs.toString(), targetArg);
                    });
                }
                printLog("获取参数如下:", sourceAnnotationValue);
                super.visitAnnotation(jcAnnotation);
            }
        });
    }

    private JCTree.JCAnnotation makeAnnotation(String annotaionName, List<JCTree.JCExpression> args) {
        JCTree.JCExpression expression=chainDots(annotaionName.split("\\."));
        JCTree.JCAnnotation jcAnnotation=treeMaker.Annotation(expression, args);
        return jcAnnotation;
    }

    private void addProviderImport(Element element) {
        TreePath treePath = trees.getPath(element);
        JCTree.JCCompilationUnit jccu = (JCTree.JCCompilationUnit) treePath.getCompilationUnit();
        java.util.List<JCTree> trees = new ArrayList<>();
        trees.addAll(jccu.defs);
        JCTree.JCImport feignImport = buildImport(PackageSupportEnum.Autowired.getPackageName(), PackageSupportEnum.Autowired.getClassName());
        JCTree.JCImport requestBody = buildImport(PackageSupportEnum.PostMapping.getPackageName(), PackageSupportEnum.PostMapping.getClassName());
        JCTree.JCImport requestMapping = buildImport(PackageSupportEnum.RequestMapping.getPackageName(), PackageSupportEnum.RequestMapping.getClassName());
        if (!trees.contains(feignImport)) {
            trees.add(0,feignImport);
        }
        if (!trees.contains(requestBody)) {
            trees.add(0,requestBody);
        }
        if (!trees.contains(requestMapping)) {
            trees.add(0,requestMapping);
        }
        printLog("import trees{}", trees.toString());
        jccu.defs = from(trees);
    }

    private JCTree.JCImport buildImport(String packageName, String className) {
        JCTree.JCIdent ident = treeMaker.Ident(names.fromString(packageName));
        JCTree.JCImport jcImport = treeMaker.Import(treeMaker.Select(
                ident, names.fromString(className)), false);
        printLog("add Import:{}", jcImport.toString());
        return jcImport;
    }

    private void printLog(String ss, Object... args) {
        if (args.length > 0) {
            ss = ss.replace("{}", "%s");
            String logs = String.format(ss, args);
            messager.printMessage(Diagnostic.Kind.NOTE, logs);
        } else {
            messager.printMessage(Diagnostic.Kind.NOTE, ss);
        }
    }
    public JCTree.JCExpression makeArg(String key,String value)
    {
        JCTree.JCExpression arg = treeMaker.Assign(treeMaker.Ident(names.fromString(key)), treeMaker.Literal(value));
        return arg;
    }
    public  JCTree.JCExpression chainDots(String... elems) {
        assert elems != null;

        JCTree.JCExpression e = null;
        for (int i = 0 ; i < elems.length ; i++) {
            e = e == null ? treeMaker.Ident(names.fromString(elems[i])) : treeMaker.Select(e, names.fromString(elems[i]));
        }
        assert e != null;

        return e;
    }
    private enum PackageSupportEnum {
        RequestBody("org.springframework.web.bind.annotation", "RequestBody"),
        RequestMapping("org.springframework.web.bind.annotation", "RequestMapping"),
        PostMapping("org.springframework.web.bind.annotation", "PostMapping"),
        Autowired("org.springframework.beans.factory.annotation", "Autowired"),
        RestController("org.springframework.web.bind.annotation", "RestController")

        ;


        private String packageName;
        private String className;

        PackageSupportEnum(String packageName, String className) {

            this.packageName = packageName;
            this.className = className;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return packageName+"."+className;
        }
    }
}