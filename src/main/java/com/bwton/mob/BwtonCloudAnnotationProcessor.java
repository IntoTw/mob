package com.bwton.mob;

import com.sun.source.tree.Tree;
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

/**
 * @author Chenxiang
 * @Description:
 * @create 2019/12/17 20:31
 */
@SupportedAnnotationTypes("com.bwton.*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BwtonCloudAnnotationProcessor extends AbstractProcessor {
    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    Map<String, JCTree.JCAssign> consumerSourceAnnotationValue=new HashMap<>();
    Map<String, JCTree.JCAssign> providerSourceAnnotationValue=new HashMap<>();

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
        handleConsumer(roundEnv);
        handleProvider(roundEnv);
        return true;
    }
    private void handleConsumer(RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(BwtonCloudConsumer.class);
        set.forEach(element -> {
            buildConsumerSourceAnnotationValue(element);
            consumerSourceAnnotationValue.forEach((key, value) -> {
                printLog("key: {} value: {}",key,value);
            });
            addImport(element,PackageSupportEnum.FeignClient,PackageSupportEnum.RequestBody,PackageSupportEnum.RequestMapping,PackageSupportEnum.RequestParam);
            addClassAnnotation(element);
            addMethodAnnotation(element);

            JCTree jcTree=trees.getTree(element);
            printLog("result :{}",jcTree);
        });
    }
    private boolean handleProvider(RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(BwtonCloudProvider.class);
        //获取模板类
        //构建内部类
        set.forEach(element -> {
            addImport(element,PackageSupportEnum.Autowired,PackageSupportEnum.PostMapping,PackageSupportEnum.RequestMapping);
            buildProviderSourceAnnotationValue(element);
            JCTree jcTree = trees.getTree(element);
            final java.util.List<JCTree.JCMethodDecl> methodDecls = new ArrayList<>();
            //获取需要处理的方法
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
                    //modify by chenxiang  2019/12/20 //只处理public方法
                    if((jcMethodDecl.mods.flags|Flags.PUBLIC)==Flags.PUBLIC){
                        List<JCTree.JCAnnotation> methodAnnotations=jcMethodDecl.mods.annotations;
                        java.util.List<JCTree.JCAnnotation> target = new ArrayList<>(methodAnnotations);
                        target.removeIf(jcAnnotation -> jcAnnotation.annotationType.toString().contains("Override"));
                        jcMethodDecl.mods.annotations=List.from(target);
                        methodDecls.add(jcMethodDecl);
                    }
                    super.visitMethodDef(jcMethodDecl);
                }
            });
            //根据方法自动生成controller
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    JCTree.JCClassDecl jcClassDecl1 = buildInnerClass(jcClassDecl, methodDecls);
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
    private void buildConsumerSourceAnnotationValue(Element element) {
        JCTree jcTree = trees.getTree(element);
        //获取源注解的参数
        jcTree.accept(new TreeTranslator(){
            @Override
            public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                JCTree.JCIdent jcIdent=(JCTree.JCIdent)jcAnnotation.getAnnotationType();
                if(jcIdent.name.contentEquals("BwtonCloudConsumer")){
                    printLog("class Annotation arg process:{}",jcAnnotation.toString());
                    jcAnnotation.args.forEach(e->{
                        JCTree.JCAssign jcAssign=(JCTree.JCAssign)e ;
                        JCTree.JCIdent value = treeMaker.Ident(names.fromString("value"));
                        JCTree.JCAssign targetArg=treeMaker.Assign(value,jcAssign.rhs);
                        consumerSourceAnnotationValue.put(jcAssign.lhs.toString(),targetArg);
                    });
                }
                printLog("获取参数如下:",consumerSourceAnnotationValue);
                super.visitAnnotation(jcAnnotation);
            }
        });
    }
    private void buildProviderSourceAnnotationValue(Element element) {
        JCTree jcTree = trees.getTree(element);
        //获取源注解的参数
        jcTree.accept(new TreeTranslator(){
            @Override
            public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                JCTree.JCIdent jcIdent=(JCTree.JCIdent)jcAnnotation.getAnnotationType();
                if(jcIdent.name.contentEquals("BwtonCloudProvider")){
                    printLog("class Annotation arg process:{}",jcAnnotation.toString());
                    jcAnnotation.args.forEach(e->{
                        JCTree.JCAssign jcAssign=(JCTree.JCAssign)e ;
                        JCTree.JCIdent value = treeMaker.Ident(names.fromString("value"));
                        JCTree.JCAssign targetArg=treeMaker.Assign(value,jcAssign.rhs);
                        providerSourceAnnotationValue.put(jcAssign.lhs.toString(),targetArg);
                    });
                }
                printLog("获取参数如下:",providerSourceAnnotationValue);
                super.visitAnnotation(jcAnnotation);
            }
        });
    }
    private void addMethodAnnotation(Element element) {
        JCTree jcTree = trees.getTree(element);
        jcTree.accept(new TreeTranslator(){
            @Override
            public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                jcClassDecl.defs.forEach(e->{
                    if(e.getKind().equals(Tree.Kind.METHOD)){
                        printLog("method.preDef:{}" , e.toString());
                        JCTree.JCMethodDecl jcMethodDecl= (JCTree.JCMethodDecl) e;
                        //最基本的构造方法不处理
                        if(!jcMethodDecl.name.contentEquals("<init>")){
                            List<JCTree.JCExpression> args;
                            //增加方法注解
                            List<JCTree.JCExpression> jcAssigns = getSourceArgs("requestMappingPrefix",true);
                            if(jcAssigns!=null && jcAssigns.size()>0) {
                                JCTree.JCAssign expression = (JCTree.JCAssign) jcAssigns.get(0);
                                String newArgValue=expression.rhs.toString()
                                        .replace("\"","")
                                        .concat("/")
                                        .concat(jcMethodDecl.name.toString());
                                JCTree.JCExpression newArg=makeArg("value",newArgValue);
                                args=List.of(newArg);
                            }else {
                                JCTree.JCExpression expression=makeArg("value","/"+jcMethodDecl.name.toString());
                                args=List.of(expression);
                            }
                            JCTree.JCAnnotation jcAnnotation = makeAnnotation(PackageSupportEnum.RequestMapping.toString(), args);
                            jcMethodDecl.mods.annotations = jcMethodDecl.mods.annotations.append(jcAnnotation);
                            //增加参数注解
                            if (jcMethodDecl.params.size() > 0) {
                                for (int i = 0; i < jcMethodDecl.params.size() ; i++) {
                                    JCTree.JCVariableDecl jcVariableDecl=jcMethodDecl.params.get(i);
                                    if(i==0){
                                        //第一个参数加requestbody注解，其他参数加requestparam注解，否则会报错
                                        jcVariableDecl.mods.annotations = jcVariableDecl.mods.annotations.append(makeAnnotation(PackageSupportEnum.RequestBody.toString(), List.nil()));
                                    }else {
                                        JCTree.JCAnnotation requestParam=makeAnnotation(PackageSupportEnum.RequestParam.toString(),
                                                List.of(makeArg("value",jcVariableDecl.name.toString())));
                                        jcVariableDecl.mods.annotations = jcVariableDecl.mods.annotations.append(requestParam);
                                    }

                                }
                            }
                        }
                        printLog("method.endDef:{}" , e.toString());
                    }
                });
                super.visitClassDef(jcClassDecl);
            }
        });
    }

    private void addClassAnnotation(Element element) {
        JCTree jcTree = trees.getTree(element);
        //获取源注解的参数
        jcTree.accept(new TreeTranslator(){
            @Override
            public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                List<JCTree.JCExpression> jcAssigns = getSourceArgs("feignClientPrefix",true);
                JCTree.JCExpression arg;
                if(jcAssigns==null || jcAssigns.size()==0){
                    arg=makeArg("value","");
                }
                else{
                    arg=argClone((JCTree.JCAssign) jcAssigns.get(0));
                }
                printLog("jcAssigns :{}",jcAssigns);
                JCTree.JCAnnotation jcAnnotation=makeAnnotation(PackageSupportEnum.FeignClient.toString(),List.of(arg));
                printLog("class Annotation add:{}",jcAnnotation.toString());
                jcClassDecl.mods.annotations=jcClassDecl.mods.annotations.append(jcAnnotation);
                jcClassDecl.mods.annotations.forEach(e -> {
                    printLog("class Annotation list:{}",e.toString());
                });
                super.visitClassDef(jcClassDecl);
            }
        });
    }
    private JCTree.JCExpression argClone(JCTree.JCAssign jcAssign ){
        return makeArg(jcAssign.lhs.toString(), jcAssign.rhs.toString().replace("\"",""));
    }
    private List<JCTree.JCExpression> getSourceArgs(String argName,boolean isConsumer) {
        JCTree.JCAssign jcAssign = isConsumer?consumerSourceAnnotationValue.get(argName):providerSourceAnnotationValue.get(argName);
        if(jcAssign==null){
            return List.nil();
        }
        java.util.List<JCTree.JCAssign> jcAssigns=new ArrayList<>();
        JCTree.JCAssign assign = assignClone(jcAssign);
        jcAssigns.add(assign);
        jcAssigns.forEach(e -> {
            printLog("jcAssigns  :{}", e);
        });
        return List.from(jcAssigns);
    }
    private JCTree.JCAssign assignClone(JCTree.JCAssign jcAssign){
        JCTree.JCIdent left=treeMaker.Ident(names.fromString(jcAssign.lhs.toString()));
        JCTree.JCIdent right=treeMaker.Ident(names.fromString(jcAssign.rhs.toString()));
        return treeMaker.Assign(left,right);
    }
    private JCTree.JCAnnotation makeAnnotation(String annotaionName, List<JCTree.JCExpression> args)
    {
        JCTree.JCExpression expression=chainDots(annotaionName.split("\\."));
        JCTree.JCAnnotation jcAnnotation=treeMaker.Annotation(expression, args);
        return jcAnnotation;
    }
    private void addImport(Element element,PackageSupportEnum... packageSupportEnums) {
        TreePath treePath = trees.getPath(element);
        JCTree.JCCompilationUnit jccu = (JCTree.JCCompilationUnit) treePath.getCompilationUnit();
        java.util.List<JCTree> trees = new ArrayList<>();
        trees.addAll(jccu.defs);
        java.util.List<JCTree> sourceImportList = new ArrayList<>();
        trees.forEach(e->{
            if(e.getKind().equals(Tree.Kind.IMPORT)){
                sourceImportList.add(e);
            }
        });
        java.util.List<JCTree.JCImport> needImportList=buildImportList(packageSupportEnums);
        for (int i = 0; i < needImportList.size(); i++) {
            boolean importExist=false;
            for (int j = 0; j < sourceImportList.size(); j++) {
                if(sourceImportList.get(j).toString().equals(needImportList.get(i).toString())){
                    importExist=true;
                }
            }
            if(!importExist){
                trees.add(0,needImportList.get(i));
            }
        }
        printLog("import trees{}",trees.toString());
        jccu.defs=List.from(trees);
    }

    private java.util.List<JCTree.JCImport> buildImportList(PackageSupportEnum... packageSupportEnums) {
        java.util.List<JCTree.JCImport> targetImportList =new ArrayList<>();
        if(packageSupportEnums.length>0){
            for (int i = 0; i < packageSupportEnums.length; i++) {
                JCTree.JCImport needImport = buildImport(packageSupportEnums[i].getPackageName(),packageSupportEnums[i].getClassName());
                targetImportList.add(needImport);
            }
        }
        return targetImportList;
    }

    private JCTree.JCImport buildImport(String packageName, String className) {
        JCTree.JCIdent ident = treeMaker.Ident(names.fromString(packageName));
        JCTree.JCImport jcImport = treeMaker.Import(treeMaker.Select(
                ident, names.fromString(className)), false);
        printLog("add Import:{}",jcImport.toString());
        return jcImport;
    }
    private void printLog(String ss,Object... args){
        if(args.length>0) {
            ss = ss.replace("{}", "%s");
            String logs = String.format(ss, args);
            messager.printMessage(Diagnostic.Kind.NOTE, logs);
        }else{
            messager.printMessage(Diagnostic.Kind.NOTE, ss);
        }
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
    public JCTree.JCExpression makeArg(String key,String value)
    {
        JCTree.JCExpression arg = treeMaker.Assign(treeMaker.Ident(names.fromString(key)), treeMaker.Literal(value));
        return arg;
    }
    private JCTree.JCClassDecl buildInnerClass(JCTree.JCClassDecl sourceClassDecl, java.util.List<JCTree.JCMethodDecl> methodDecls) {
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
        JCTree.JCExpression jcAssign=makeArg("value",providerSourceAnnotationValue.get("feignClientPrefix").rhs.toString().replace("\"",""));
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
            if(!e.restype.toString().contains("void")){
                JCTree.JCReturn jcReturn=treeMaker.Return(exec.getExpression());
                e.body.stats = e.body.stats.append(jcReturn);
            }else {
                e.body.stats = e.body.stats.append(exec);
            }


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

    public enum PackageSupportEnum {
        FeignClient("org.springframework.cloud.netflix.feign", "FeignClient"),
        RequestBody("org.springframework.web.bind.annotation", "RequestBody"),
        RequestMapping("org.springframework.web.bind.annotation", "RequestMapping"),
        PostMapping("org.springframework.web.bind.annotation", "PostMapping"),
        Autowired("org.springframework.beans.factory.annotation", "Autowired"),
        RestController("org.springframework.web.bind.annotation", "RestController"),
        RequestParam("org.springframework.web.bind.annotation", "RequestParam"),
        Override("java.lang", "Override");

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
            return this.packageName+"."+this.className;
        }
    }
}