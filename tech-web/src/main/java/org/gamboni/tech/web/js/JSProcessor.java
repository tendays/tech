package org.gamboni.tech.web.js;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_17;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

@SupportedAnnotationTypes(
  "org.gamboni.tech.web.js.JS")
@SupportedSourceVersion(RELEASE_17)
@AutoService(Processor.class)
public class JSProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (var annotation : annotations) {
            for (Element elt : roundEnvironment.getElementsAnnotatedWith(annotation)) {
                // class com.sun.tools.javac.code.Symbol$ClassSymbol, kind RECORD
                Messager messager = processingEnv.getMessager();
                elt.accept(new ElementVisitor<Void, Void>() {
                    @Override
                    public Void visit(Element e, Void unused) {
                        messager.printMessage(NOTE, "Element " + e);
                        return null;
                    }

                    @Override
                    public Void visitPackage(PackageElement e, Void unused) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Void visitType(TypeElement e, Void unused) {
                        emitJsType(e.getQualifiedName().toString(),
                                e.getRecordComponents()
                                        .stream()
                                        .map(comp -> comp.getSimpleName().toString())
                                        .toList());

                        return null;
                    }

                    @Override
                    public Void visitVariable(VariableElement e, Void unused) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Void visitExecutable(ExecutableElement e, Void unused) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Void visitTypeParameter(TypeParameterElement e, Void unused) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Void visitUnknown(Element e, Void unused) {
                        throw new UnsupportedOperationException();
                    }
                }, null);
            }
        }
        return true;
    }

    private void emitJsType(String javaType, List<String> attributes) {
        int dot = javaType.lastIndexOf('.');

        String packageName = javaType.substring(0, dot);

        String jsType = "Js" + javaType.substring(dot + 1);

        String jsQualifiedType = packageName + "." + jsType;
        processingEnv.getMessager().printMessage(NOTE, "Writing " + jsQualifiedType);
        try {
            JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(jsQualifiedType);
            try (var out = sourceFile.openWriter()) {
                out.write("package " + packageName + ";\n" +
                        "\n" +
                        "import " + JavaScript.class.getName() +";\n" +
                        "import " + JavaScript.JsExpression.class.getName().replace("$", ".") +";\n" +
                        "\n" +
                        "public class " + jsType + " implements JsExpression {\n" +
                        "  private final JsExpression delegate;\n" +
                        "\n" +
                        "  public " + jsType + "(JsExpression delegate) {\n" +
                        "    this.delegate = delegate;\n" +
                        "  }\n" +
                        "\n" +
                        "  public " + jsType + "(String js) {\n" +
                        "    this.delegate = s -> js;\n" +
                        "  }\n" +
                        "\n" +
                        "  @Override\n" +
                        "  public String format(JavaScript.Scope s) {\n" +
                        "    return delegate.format(s);\n" +
                        "  }\n");
                for (var attribute : attributes) {
                    out.write("\n" +
                            "  public JsExpression " + attribute + "() {" +
                            "    return this.dot(\"" + attribute + "\");" +
                            "  }\n"
                    );
                }
                out.write("}\n");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(ERROR, "Could not generate " + jsQualifiedType +": " + e);
        }
    }
}
