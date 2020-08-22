package de.peass.dependency.traces;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;

import de.peass.dependency.traces.requitur.content.TraceElementContent;

public class MethodReader {
   
   private static final Logger LOG = LogManager.getLogger(MethodReader.class);
   
   private final ClassOrInterfaceDeclaration clazz;
   
   public MethodReader(ClassOrInterfaceDeclaration clazz) {
      this.clazz = clazz;
   }
   
   public Node getMethod(final Node node, final TraceElementContent currentTraceElement) {
      if (node != null && node.getParentNode().isPresent()) {
         final Node parent = node.getParentNode().get();
         if (node instanceof MethodDeclaration) {
            final MethodDeclaration method = (MethodDeclaration) node;
            if (method.getNameAsString().equals(currentTraceElement.getMethod())) {
               LOG.trace("Parameter: {} Trace-Parameter: {}", method.getParameters().size(), currentTraceElement.getParameterTypes().length);
               if (parametersEqual(currentTraceElement, method)) {
                  if (parent instanceof TypeDeclaration<?>) {
                     final TypeDeclaration<?> clazz = (TypeDeclaration<?>) parent;
                     final String clazzName = clazz.getNameAsString();
                     if (clazzName.equals(currentTraceElement.getSimpleClazz())) {
                        return method;
                     }
                  } else {
                     return method;
                  }
               }
            }
         } else if (node instanceof ConstructorDeclaration) {
            if ("<init>".equals(currentTraceElement.getMethod())) {
               if (parent instanceof TypeDeclaration<?>) {
                  final ConstructorDeclaration constructor = (ConstructorDeclaration) node;
                  final TypeDeclaration<?> clazz = (TypeDeclaration<?>) parent;
                  LOG.trace(clazz.getNameAsString() + " " + currentTraceElement.getClazz());
                  if (clazz.getNameAsString().equals(currentTraceElement.getSimpleClazz())) {
                     if (parametersEqual(currentTraceElement, constructor)) {
                        return node;
                     }
                  }
               }
               LOG.trace(parent);
            }
         }

         for (final Node child : node.getChildNodes()) {
            final Node possibleMethod = getMethod(child, currentTraceElement);
            if (possibleMethod != null) {
               return possibleMethod;
            }

         }
      }

      return null;
   }
   
   private boolean parametersEqual(final TraceElementContent te, final CallableDeclaration<?> method) {
      if (te.getParameterTypes().length == 0 && method.getParameters().size() == 0) {
         return true;
      } else if (method.getParameters().size() == 0) {
         return false;
      }
      int parameterIndex = 0;
      final List<Parameter> parameters = method.getParameters();
      String[] traceParameterTypes;
      if (te.isInnerClassCall()) {
         final String outerClazz = te.getOuterClass();
         final String firstType = te.getParameterTypes()[0];
         if (outerClazz.equals(firstType)) {
            traceParameterTypes = new String[te.getParameterTypes().length - 1];
            System.arraycopy(te.getParameterTypes(), 1, traceParameterTypes, 0, te.getParameterTypes().length - 1);
         } else {
            traceParameterTypes = te.getParameterTypes();
         }
      } else {
         traceParameterTypes = te.getParameterTypes();
      }
      if (traceParameterTypes.length != parameters.size() && !parameters.get(parameters.size() - 1).isVarArgs()) {
         return false;
      } else if (parameters.get(parameters.size() - 1).isVarArgs()) {
         if (traceParameterTypes.length < parameters.size() - 1) {
            return false;
         }
      }

      for (final Parameter parameter : parameters) {
         final Type type = parameter.getType();
         LOG.trace(type + " " + type.getClass());
         if (!parameter.isVarArgs()) {
            if (!testParameter(traceParameterTypes, parameterIndex, type, false)) {
               return false;
            }
         } else {
            if (traceParameterTypes.length > parameterIndex) {
               for (int varArgIndex = parameterIndex; varArgIndex < traceParameterTypes.length; varArgIndex++) {
                  if (!testParameter(traceParameterTypes, varArgIndex, type, true)) {
                     return false;
                  }
               }
            }
         }

         parameterIndex++;
      }

      return true;
   }

   private boolean testParameter(final String traceParameterTypes[], final int parameterIndex, final Type type, final boolean varArgAllowed) {
      final String traceParameterType = traceParameterTypes[parameterIndex];
      final String simpleTraceParameterType = traceParameterType.substring(traceParameterType.lastIndexOf('.') + 1);
      final String typeString = type instanceof ClassOrInterfaceType ? ((ClassOrInterfaceType) type).getNameAsString() : type.toString();
      // ClassOrInterfaceType
      if (typeString.equals(simpleTraceParameterType)) {
         return true;
      } else if (varArgAllowed && (typeString + "[]").equals(simpleTraceParameterType)) {
         return true;
      } else if (simpleTraceParameterType.contains("$")) {
         final String innerClassName = simpleTraceParameterType.substring(simpleTraceParameterType.indexOf("$") + 1);
         if (innerClassName.equals(typeString)) {
            return true;
         } else {
            return false;
         }
      } else if(clazz != null && clazz.getTypeParameters().size() > 0) {
         boolean isTypeParameter = isTypeParameter(typeString);
         return isTypeParameter;
      } else {
         return false;
      }
   }

   private boolean isTypeParameter(final String typeString) {
      boolean isTypeParameter = false;
      // It is too cumbersome to check whether a class really fits to the class hierarchy of a generic class; 
      // therefore, we only check whether the parameter is one of the type parameters
      for (TypeParameter parameter : clazz.getTypeParameters()) {
         if (parameter.getName().toString().equals(typeString)) {
            isTypeParameter = true;
         }
      }
      return isTypeParameter;
   }
}