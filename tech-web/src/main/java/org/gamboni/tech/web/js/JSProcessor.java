package org.gamboni.tech.web.js;

import com.google.auto.service.AutoService;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.gamboni.tech.misc.Unit;

import javax.annotation.processing.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static javax.lang.model.SourceVersion.RELEASE_17;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static org.gamboni.tech.misc.Unit.UNIT;

@SupportedAnnotationTypes(
  "org.gamboni.tech.web.js.JS")
@SupportedSourceVersion(RELEASE_17)
@AutoService(Processor.class)
public class JSProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (var annotation : annotations) {
            Messager messager = processingEnv.getMessager();
            Multimap<String, String> subtypes = HashMultimap.create();
            for (Element elt : roundEnvironment.getElementsAnnotatedWith(annotation)) {
                // class com.sun.tools.javac.code.Symbol$ClassSymbol, kind RECORD
                elt.accept(new SimpleElementVisitor14<Void, Unit>() {
                    @Override
                    public Void visitType(TypeElement e, Unit unused) {
                        for (TypeMirror iface : e.getInterfaces()) {
                            getFullyQualifiedName(iface).ifPresent(ifaceName -> {
                                subtypes.put(ifaceName, e.getQualifiedName().toString());
                            });
                        }
                        emitTypes(e.getQualifiedName().toString(),
                                e.getRecordComponents());

                        return null;
                    }
                }, UNIT);
            }
            messager.printMessage(NOTE, subtypes.toString());
        }
        return true;
    }

    private static Optional<String> getFullyQualifiedName(TypeMirror typeMirror) {
        return typeMirror.accept(new SimpleTypeVisitor14<>(){
            @Override
            public Optional<String> visitDeclared(DeclaredType t, Optional<Object> o) {
                return t.asElement().accept(new SimpleElementVisitor14<>(){
                    @Override
                    public Optional<String> visitType(TypeElement e, Optional<Object> o) {
                        return Optional.of(e.getQualifiedName().toString());
                    }
                }, Optional.empty());
            }
        }, Optional.empty());
    }

    private void emitTypes(String javaType, List<? extends RecordComponentElement> recordComponents) {
        emitFile(n -> "Js" + n, javaType, (out, recordType, jsType) -> {
            out.write(
            "import static " + JavaScript.class.getName() +".obj;\n" +
                    "import " + Map.class.getName() + ";\n" +
                    "import " + JavaScript.class.getName() + ";\n" +
                    "import " + JavaScript.JsExpression.class.getName().replace("$", ".") + ";\n" +
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
            for (var attribute : recordComponents) {
                out.write("\n" +
                        "  public JsExpression " + attribute.getSimpleName() + "() {\n" +
                        "    return this.dot(\"" + attribute.getSimpleName() + "\");\n" +
                        "  }\n"
                );
            }

            out.write("  public static JsExpression literal(");
            String sep = "\n";
            for (var attribute : recordComponents) {
                out.write(sep + "      JsExpression " + attribute.getSimpleName());
                sep = ",\n";
            }

            // TODO support shorthand for small objects, and map builders for large objects.
            out.write(") {\n" +
                    "    return obj(Map.of(");
            sep = "\n";
            for (var attribute : recordComponents) {
                out.write(sep + "      \"" + attribute.getSimpleName() + "\", " + attribute.getSimpleName());
                sep = ",\n";
            }
            out.write("));\n" +
                    "  }\n" +
                    "}\n");
        });

        List<Optional<String>> boxedTypes = recordComponents.stream().map(c -> boxedType(c.asType()))
                .toList();

        if (boxedTypes.stream().allMatch(Optional::isPresent)) {
            emitFile(n -> n + "Values", javaType, (out, recordType, valuesType) -> {
                out.write("import org.gamboni.tech.web.ui.Value;\n" +
                        "\n" +
                        "public record " + valuesType + "(");
                String sep = "";
                for (var attribute : recordComponents) {
                    TypeMirror attributeType = attribute.asType();
                    out.write(sep + "Value<" + boxedType(attributeType).get() + "> " + attribute.getSimpleName());
                    sep = ", ";
                }
                out.write(") {\n");

                writeFactoryMethod(recordComponents, out, recordType, valuesType);

                writeFactoryMethod(recordComponents, out, "Js" + recordType, valuesType);
                out.write("}");
            });
        }
    }

    private static void writeFactoryMethod(List<? extends RecordComponentElement> recordComponents, Writer out, String recordType, String valuesType) throws IOException {
        String sep;
        String factoryParameter = UPPER_CAMEL.to(LOWER_CAMEL, recordType);
        out.write(
                "\n" +
                "  public static " + valuesType + " of(" + recordType + " " + factoryParameter + ") {\n" +
                "    return new " + valuesType +"(");
        sep = "\n";
        for (var attribute : recordComponents) {
            out.write(sep + "      Value.of(" + factoryParameter +"."+ attribute.getSimpleName() +"())");
            sep = ",\n";
        }
        out.write(");\n");
        out.write("  }\n");
    }

    private static Optional<String> boxedType(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor14<>(Optional.empty()) {
            @Override
            public Optional<String> visitPrimitive(PrimitiveType t, Unit unit) {
                if (t.getKind() == TypeKind.LONG) {
                    return Optional.of("Long");
                } else {
                    return Optional.empty();
                }
            }

            @Override
            public Optional<String> visitDeclared(DeclaredType t, Unit unit) {
                if (t.getTypeArguments().size() > 0) { // collection types will drop here
                    return Optional.empty();
                } else {
                    String fqcn = t.toString();
                    if (!SUPPORTED_VALUES.contains(fqcn) && !isEnum(t)) {
                        return Optional.empty();
                    } else if (fqcn.startsWith("java.lang.")) {
                        return Optional.of(fqcn.substring("java.lang.".length()));
                    } else {
                        return Optional.of(t.toString());
                    }
                }
            }
        }, UNIT);
    }

    /** see {@code of()} method overloads in {@link org.gamboni.tech.web.ui.Value}. */
    private static final Set<String> SUPPORTED_VALUES = Set.of(
            Long.class.getName(),
            String.class.getName());

    private static boolean isEnum(DeclaredType t) {
        return t.asElement().accept(new SimpleElementVisitor14<>() {
            @Override
            public Boolean visitType(TypeElement e, Unit unit) {
                return getFullyQualifiedName(e.getSuperclass())
                        .map(Enum.class.getName()::equals)
                        .orElse(false);
            }
        }, UNIT);
    }

    private interface CodeWriter {
        void write(Writer writer, String recordType, String typeName) throws IOException;
    }

    private void emitFile(UnaryOperator<String> naming, String javaType, CodeWriter imports) {
        int dot = javaType.lastIndexOf('.');

        String packageName = javaType.substring(0, dot);

        String recordType = javaType.substring(dot + 1);
        String jsType = naming.apply(recordType);

        String jsQualifiedType = packageName + "." + jsType;
        processingEnv.getMessager().printMessage(NOTE, "Writing " + jsQualifiedType);
        try {
            JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(jsQualifiedType);
            try (var out = sourceFile.openWriter()) {
                out.write("package " + packageName + ";\n\n");

                imports.write(out, recordType, jsType);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(ERROR, "Could not generate " + jsQualifiedType +": " + e);
        }
    }
}
