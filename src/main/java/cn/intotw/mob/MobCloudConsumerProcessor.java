package cn.intotw.mob;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
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

@SupportedAnnotationTypes("cn.intotw.*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@Deprecated
public class MobCloudConsumerProcessor extends AbstractProcessor {
    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    Map<String,JCTree.JCAssign> sourceAnnotationValue=new HashMap<>();

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

        return true;
    }

    private void handleConsumer(RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(MobCloudConsumer.class);
        set.forEach(element -> {
            buildSourceAnnotationValue(element);
            sourceAnnotationValue.forEach((key, value) -> {
                printLog("key: {} value: {}",key,value);
            });
            addConsumerImport(element);
            addClassAnnotation(element);
            addMethodAnnotation(element);

            JCTree jcTree=trees.getTree(element);
            printLog("result :{}",jcTree);
        });
    }

    private void buildSourceAnnotationValue(Element element) {
        JCTree jcTree = trees.getTree(element);
        //获取源注解的参数
        jcTree.accept(new TreeTranslator(){
            @Override
            public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                JCTree.JCIdent jcIdent=(JCTree.JCIdent)jcAnnotation.getAnnotationType();
                if(jcIdent.name.contentEquals("MobCloudConsumer")){
                    printLog("class Annotation arg process:{}",jcAnnotation.toString());
                    jcAnnotation.args.forEach(e->{
                        JCTree.JCAssign jcAssign=(JCTree.JCAssign)e ;
                        JCTree.JCIdent value = treeMaker.Ident(names.fromString("value"));
                        JCTree.JCAssign targetArg=treeMaker.Assign(value,jcAssign.rhs);
                        sourceAnnotationValue.put(jcAssign.lhs.toString(),targetArg);
                    });
                }
                printLog("获取参数如下:",sourceAnnotationValue);
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
                            List<JCTree.JCExpression> jcAssigns = getSourceArgs("requestMappingPrefix");
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
                                //默认只给第一个参数加
                                List<JCTree.JCExpression> jcAssign;
                                JCTree.JCVariableDecl jcVariableDecl = jcMethodDecl.params.get(0);
                                jcVariableDecl.mods.annotations = jcVariableDecl.mods.annotations.append(makeAnnotation(PackageSupportEnum.RequestBody.toString(), List.nil()));
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
                List<JCTree.JCExpression> jcAssigns = getSourceArgs("feignClientPrefix");
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
    private List<JCTree.JCExpression> getSourceArgs(String argName) {
        JCTree.JCAssign jcAssign = sourceAnnotationValue.get(argName);
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
    private void addConsumerImport(Element element) {
        TreePath treePath = trees.getPath(element);
        JCTree.JCCompilationUnit jccu = (JCTree.JCCompilationUnit) treePath.getCompilationUnit();
        java.util.List<JCTree> trees = new ArrayList<>();
        trees.addAll(jccu.defs);
        JCTree.JCImport feignImport = buildImport(PackageSupportEnum.FeignClient.getPackageName(), PackageSupportEnum.FeignClient.getClassName());
        JCTree.JCImport requestBody = buildImport(PackageSupportEnum.RequestBody.getPackageName(), PackageSupportEnum.RequestBody.getClassName());
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

        printLog("import trees{}",trees.toString());
        jccu.defs=List.from(trees);
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
    private enum PackageSupportEnum {
        FeignClient("org.springframework.cloud.netflix.feign", "FeignClient"),
        RequestBody("org.springframework.web.bind.annotation", "RequestBody"),
        RequestMapping("org.springframework.web.bind.annotation", "RequestMapping");


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
}