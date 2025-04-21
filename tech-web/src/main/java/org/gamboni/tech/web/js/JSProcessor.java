package org.gamboni.tech.web.js;

import com.google.auto.service.AutoService;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.misc.Unit;
import org.gamboni.tech.web.ui.value.DateValue;
import org.gamboni.tech.web.ui.value.NumberValue;
import org.gamboni.tech.web.ui.value.Value;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
        // maps interface names to implementing types which have a @JS annotation
        Multimap<String, String> subtypes = HashMultimap.create();
        Set<String> incompleteTypes = new HashSet<>();
        for (var annotation : annotations) {
            Messager messager = processingEnv.getMessager();
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

                        if (emitTypes(e.getQualifiedName().toString(),
                                isPolymorphic(e),
                                e.getRecordComponents()).isIncomplete()) {
                            incompleteTypes.add(e.getQualifiedName().toString());
                        }

                        return null;
                    }
                }, UNIT);
            }
            messager.printMessage(NOTE, subtypes.toString());
            subtypes.asMap().forEach((iface, implementors) -> {
                if (Iterables.any(implementors, incompleteTypes::contains)) {
                    // TODO would be good to clarify why
                    processingEnv.getMessager().printMessage(NOTE, "Not generating " + iface +"Values " +
                            "because it does not have full back-end support");
                    return;
                }
                emitFile(n -> n + "Values", iface, ((writer, recordType, typeName) -> {
                    String ifaceInstance = UPPER_CAMEL.to(LOWER_CAMEL, recordType);
                    writer.write("public interface " + typeName + " {\n")
                            .write("    public static ").write(JsType.class)
                            .write("<? extends ").write(recordType).write("> of(")
                            .write(recordType).write(" ").write(ifaceInstance).write(") {\n");
                    String _else = "        ";
                    for (var implementor : implementors) {
                        String implName = writer.typeName(implementor);
                        String implInstance = UPPER_CAMEL.to(LOWER_CAMEL, implName);
                        writer.write(_else).write("if (").write(ifaceInstance).write(" instanceof ")
                                .write(implName).write(" ").write(implInstance).write(") {\n")
                                .write("            return ").write(implName).write("Values.of(")
                                .write(implInstance).write(");\n");
                        _else = "        } else ";
                    }
                    writer.write("        }\n")
                            .write("        throw new IllegalArgumentException();\n")
                            .write("    }\n")
                            .write("}");
                }));
            });
        }
        return true;
    }

    private record EmitOutcome(boolean isIncomplete) {}

    /** Whether the given type is part of a Jackson type hierarchy. Currently only supports types
     * directly implementing an interface annotated with {@code @JsonSubTypes}. Doesn't even check
     * that {@code type} is listed in the subtypes of the super-interface.
     * @param type type to check
     * @return true if {@code type} is part of a Jackson type hierarchy, and therefore should carry
     * a {@code @type} attribute with the type's simple name (other metadata formats are not supported.)
     */
    private static boolean isPolymorphic(TypeElement type) {
        return type.getInterfaces()
                .stream()
                .flatMap(iface -> asTypeElement(iface).stream())
                .anyMatch(iface -> iface.getAnnotationMirrors()
                .stream()
                .anyMatch(ann -> getFullyQualifiedName(ann.getAnnotationType())
                        .map(n -> n
                        .equals("com.fasterxml.jackson.annotation.JsonSubTypes"))
                        .orElse(false)));
    }

    private static Optional<String> getFullyQualifiedName(TypeMirror typeMirror) {
        return asTypeElement(typeMirror).map(t -> t.getQualifiedName().toString());
    }

    private static Optional<TypeElement> asTypeElement(TypeMirror type) {
        return getDeclaredType(type).flatMap(t -> t.asElement().accept(new SimpleElementVisitor14<>(Optional.empty()) {
            @Override
            public Optional<TypeElement> visitType(TypeElement e, Object __) {
                return Optional.of(e);
            }
        }, null));
    }

    private static Optional<DeclaredType> getDeclaredType(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor14<>(Optional.empty()) {
            @Override
            public Optional<DeclaredType> visitDeclared(DeclaredType t, Object __) {
                return Optional.of(t);
            }
        }, null);
    }


    private EmitOutcome emitTypes(String javaType, boolean includeTypeName, List<? extends RecordComponentElement> recordComponents) {
        boolean backendSupport = Iterables.all(recordComponents,
                attribute -> boxedType(attribute.asType())
                        .isPresent());
        boolean nonTrivialSerialiser = recordComponents.stream()
        .flatMap(attribute -> boxedType(attribute.asType()).stream())
                .anyMatch(t -> t.equals(Instant.class.getName()));

            emitFile(n -> n + "Values", javaType, (CodeWriter out, String recordType, String valuesType) -> {
                class ValuesRecordCode {
                    void emit() throws IOException {
                        out.write("public record " + valuesType + "(");

                        if (nonTrivialSerialiser) {
                            out.write(JavaScript.JsExpression.class)
                                    .write(" raw, ");
                        }

                        out.write(JavaScript.JsExpression.class)
                                .write(" self");

                        for (var attribute : recordComponents) {
                            TypeMirror attributeType = attribute.asType();
                            out.write(", " + boxedType(attributeType)
                                    .map(t ->
                                            getWrapperValueType(t).map(out::typeName)
                                                    .orElse(out.typeName(Value.class) + "<" + t + ">")
                                    ).orElse(out.typeName(JavaScript.JsExpression.class)) + " " + attribute.getSimpleName());
                        }
                        out.write(") implements ").write(JsType.class).write("<" + recordType + "> {\n");

                        if (nonTrivialSerialiser) {
                            // still create a constructor without explicit 'raw' parameter
                            out.write("  private ").write(valuesType).write("(");

                            out.write(JavaScript.JsExpression.class)
                                    .write(" self");

                            for (var attribute : recordComponents) {
                                TypeMirror attributeType = attribute.asType();
                                out.write(", " + boxedType(attributeType)
                                        .map(t ->
                                                getWrapperValueType(t).map(out::typeName)
                                                        .orElse(out.typeName(Value.class) + "<" + t + ">")
                                        ).orElse(out.typeName(JavaScript.JsExpression.class)) + " " + attribute.getSimpleName());
                            }

                            out.write(") {\n");
                            out.write("    this(self, self");

                            for (var attribute : recordComponents) {
                                out.write(", " + attribute.getSimpleName());
                            }
                            out.write(");\n");
                            out.write("  }\n");
                        }

                        if (backendSupport) {
                            writeFactoryMethod(recordType,
                                    List.of(factoryParameter -> objectLiteral(
                                            attr -> {
                                                String javaValue = factoryParameter + "." + attr.getSimpleName() + "()";
                                                if (boxedType(attr.asType()).map(t -> t.equals(Instant.class.getName())).orElse(false)) {
                                                    // behave like in json serialisation: instants are converted to the corresponding epoch second count
                                                    return out.typeName(JavaScript.class) + ".literal(" + javaValue + ".getEpochSecond())";
                                                } else {
                                                    return out.typeName(JavaScript.class) + ".literal(" + javaValue + ")";
                                                }
                                            })),
                                    (factoryParameter, attribute) ->
                                            // extra XValue.of() wrapping never necessary thanks to Value.of() overloads that do it already.
                                            out.typeName(Value.class) + ".of(" + factoryParameter + "." + attribute.getSimpleName() + "())");
                        }

                        if (nonTrivialSerialiser) {
                            writeFactoryMethod("JsExpression",
                                    List.of(
                                            factoryParameter -> factoryParameter, // raw
                                            factoryParameter -> // self
                                                objectLiteral(attribute -> deserialise(attribute, factoryParameter
                                                             + ".dot(\"" +  attribute.getSimpleName() + "\")"))),
                                    (factoryParameter, attribute) ->
                                            wrapIfNecessary(out, attribute,
                                                    out.typeName(Value.class) + ".of(" +
                                                            deserialise(attribute,
                                                            factoryParameter + ".dot(\"" + attribute.getSimpleName() + "\")") +")"));

                        } else {
                            writeFactoryMethod("JsExpression",
                                    List.of(
                                            factoryParameter -> factoryParameter), // self
                                    (factoryParameter, attribute) ->
                                            wrapIfNecessary(out, attribute,
                                                    out.typeName(Value.class) + ".of(" + factoryParameter + ".dot(\"" + attribute.getSimpleName() + "\"))"));

                        }

                        out.write("  public static " + valuesType + " literal(");
                        String sep = "\n";
                        for (var attribute : recordComponents) {
                            out.write(sep + "      ").write(JavaScript.JsExpression.class).write(" " + attribute.getSimpleName());
                            sep = ",\n";
                        }

                        out.write(") {\n" +
                                "    return ");

                        out.write("new " + valuesType + "(")
                                .write(objectLiteral(
                                attr -> attr.getSimpleName().toString()));
                        for (var attribute : recordComponents) {
                            out.write(",\n      " + wrapIfNecessary(out, attribute, out.typeName(Value.class) +
                                    ".of(" + attribute.getSimpleName() + ")"));
                        }
                        out.write(");\n" +
                                "  }\n\n");

                        out.write("  @Override\n" +
                                "  public Class<" + recordType + "> getBackendType() {\n" +
                                "    return " + recordType + ".class;\n" +
                                "  }\n" +
                                "\n");

                        out.write("  @Override\n" +
                                // keep "Scope" qualified in generated code
                                "  public String format(").write(JavaScript.class).write("""
                                .Scope scope) {
                                    return self.format(scope);
                                  }
                                
                                """);

                        out.write("  @Override\n" +
                                "  public ").write(JavaScript.class).write("""
                                .Precedence getPrecedence() {
                                    return self.getPrecedence();
                                  }
                                
                                """);

                        out.write("  @Override\n" +
                                "  public ").write(List.class).write("<").write(JavaScript.class).write("""
                                .Symbol> getFreeSymbols() {
                                    return self.getFreeSymbols();
                                  }
                                """);

                        if (nonTrivialSerialiser) {
                            out.write("  @Override\n" +
                                    "  public ").write(JavaScript.JsExpression.class).write(" isThisType() {\n" +
                                    "    return raw.dot(\"@type\").eq(\"" + recordType + "\");\n" +
                                    "  }\n");
                        }

                        out.write("}");
                    }

                    private String deserialise(RecordComponentElement attribute, String rawValue) {
                        if (boxedType(attribute.asType()).map(t -> t.equals(Instant.class.getName())).orElse(false)) {
                            return out.typeName(JavaScript.class) + ".newDate(" + rawValue +
                                    ".times(1000))"; // newDate() expects milliseconds but Instant is serialised as seconds
                        } else {
                           return rawValue;
                        }
                    }

                    private void writeFactoryMethod(String paramType,
                                                    List<UnaryOperator<String>> self,
                                                    BiFunction<String, RecordComponentElement, String> parameterFunction) throws IOException {
                        String factoryParameter = UPPER_CAMEL.to(LOWER_CAMEL, recordType);
                        out.write(
                                "\n" +
                                        "  public static " + valuesType + " of(" + paramType + " " + factoryParameter + ") {\n" +
                                        "    return new " + valuesType + "(");
                        String sep = "";
                        for (var param : self) {
                            out.write(sep).write(param.apply(factoryParameter));
                            sep = ", ";
                        }
                        for (var attribute : recordComponents) {
                            out.write(",\n" +
                                    "      " + parameterFunction.apply(factoryParameter, attribute));
                        }
                        out.write(");\n");
                        out.write("  }\n");
                    }

                    private String objectLiteral(Function<RecordComponentElement, String> attributeValue) {
                        var builder = new ObjectLiteralBuilder(out);
                        if (includeTypeName) {
                            builder.put("@type", out.typeName(JavaScript.class) + ".literal(\"" + recordType +"\")");
                        }
                        for (var attribute : recordComponents) {
                            builder.put(attribute.getSimpleName().toString(), attributeValue.apply(attribute));
                        }
                        return builder.get();
                    }
                }
                new ValuesRecordCode().emit();
            });
            return new EmitOutcome(!backendSupport);
    }

    private record ObjectElement(String attribute, String value) {}

    @RequiredArgsConstructor
    private static class ObjectLiteralBuilder implements Supplier<String> {
        private final CodeWriter out;
        List<ObjectElement> elements = new ArrayList<>();

        void put(String attribute, String value) {
            elements.add(new ObjectElement(attribute, value));
        }

        public String get() {
            StringBuilder code = new StringBuilder();
            code.append(out.typeName(JavaScript.class)).append(".obj(");
            if (elements.size() > 2) {
                // TODO use Map.builder() if object is larger than allowed by Map.of(...)
                code.append(out.typeName(Map.class)).append(".of(");
            }
            String sep = "\n";
            for (var element : elements) {
                code.append(sep + "      \"" + element.attribute + "\", ");
                code.append(element.value);
                sep = ",\n";
            }

            if (elements.size() > 2) {
                code.append(")"); // close Map.of()
            }
            code.append(")");
            return code.toString();
        }
    }

    /** wrap the given plain value in the wrapper type, if applicable */
    private static String wrapIfNecessary(CodeWriter out, RecordComponentElement attribute, String plainValue) {
        return boxedType(attribute.asType())
                .flatMap(boxedType -> getWrapperValueType(boxedType))
                .map(wrapper -> out.typeName(wrapper) + ".of(" + plainValue + ")")
                .orElse(plainValue);
    }

    private static Optional<Class<?>> getWrapperValueType(String boxedType) {
        if (boxedType.equals(Instant.class.getName())) {
            return Optional.of(DateValue.class);
        } else if (Set.of("Long", "Double", "Integer").contains(boxedType)) {
            return Optional.of(NumberValue.class);
        } else {
            return Optional.empty();
        }
    }

    private static Optional<String> boxedType(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor14<>(Optional.empty()) {
            @Override
            public Optional<String> visitPrimitive(PrimitiveType t, Unit unit) {
                if (t.getKind() == TypeKind.LONG) {
                    return Optional.of("Long");
                } else if (t.getKind() == TypeKind.DOUBLE) {
                    return Optional.of("Double");
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

    /** see {@code of()} method overloads in {@link Value}. */
    private static final Set<String> SUPPORTED_VALUES = Set.of(
            Double.class.getName(),
            Instant.class.getName(),
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

    private interface Code {
        void write(CodeWriter writer, String recordType, String typeName) throws IOException;
    }

    private interface CodeWriter {
        String typeName(Class<?> type);

        String typeName(String fqcn);

        CodeWriter write(Class<?> type);

        CodeWriter write(String text);
    }

    private void emitFile(UnaryOperator<String> naming, String javaType, Code code) {
        int dot = javaType.lastIndexOf('.');

        String packageName = javaType.substring(0, dot);

        String recordType = javaType.substring(dot + 1);
        String jsType = naming.apply(recordType);

        String jsQualifiedType = packageName + "." + jsType;
        processingEnv.getMessager().printMessage(NOTE, "Writing " + jsQualifiedType);
        try {
            JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(jsQualifiedType);
            var imports = new TreeSet<String>();
            var classBody = new StringBuilder();
            var cw = new CodeWriter() {

                @Override
                public String typeName(Class<?> type) {
                    imports.add(type.getName().replace('$', '.'));
                    return type.getSimpleName();
                }

                @Override
                public CodeWriter write(Class<?> type) {
                    return write(typeName(type));
                }

                @Override
                public String typeName(String fqcn) {
                    int dot = fqcn.lastIndexOf('.');
                    if (!fqcn.substring(0, dot).equals(packageName)) {
                        imports.add(fqcn);
                    }
                    return fqcn.substring(dot + 1);
                }

                @Override
                public CodeWriter write(String text) {
                    classBody.append(text);
                    return this;
                }
            };
            code.write(cw, recordType, jsType);
            try (var out = sourceFile.openWriter()) {
                out.write("package " + packageName + ";\n\n");
                for (var fqcn : imports) {
                    out.write("import " + fqcn + ";\n");
                }
                if (!imports.isEmpty()) {
                    out.write("\n");
                }
                out.write(classBody.toString());
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(ERROR, "Could not generate " + jsQualifiedType +": " + e);
        }
    }
}
