/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.fixes;

import static com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH;
import static com.google.errorprone.BugPattern.MaturityLevel.MATURE;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.MaturityLevel;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ReturnTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.doctree.LinkTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTreePathScanner;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.tree.JCTree;
import java.lang.annotation.Retention;
import javax.lang.model.element.Modifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author cushon@google.com (Liam Miller-Cushon) */
@RunWith(JUnit4.class)
public class SuggestedFixesTest {

  @Retention(RUNTIME)
  public @interface EditModifiers {
    String value() default "";

    EditKind kind() default EditKind.ADD;

    enum EditKind {
      ADD,
      REMOVE
    }
  }

  @BugPattern(
    name = "EditModifiers",
    category = Category.ONE_OFF,
    summary = "Edits modifiers",
    severity = SeverityLevel.ERROR,
    maturity = MaturityLevel.EXPERIMENTAL
  )
  public static class EditModifiersChecker extends BugChecker implements VariableTreeMatcher {

    static final ImmutableMap<String, Modifier> MODIFIERS_BY_NAME = createModifiersByName();

    private static ImmutableMap<String, Modifier> createModifiersByName() {
      ImmutableMap.Builder<String, Modifier> builder = ImmutableMap.builder();
      for (Modifier mod : Modifier.values()) {
        builder.put(mod.toString(), mod);
      }
      return builder.build();
    }

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
      EditModifiers editModifiers =
          ASTHelpers.getAnnotation(
              ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class), EditModifiers.class);
      Modifier mod = MODIFIERS_BY_NAME.get(editModifiers.value());
      Verify.verifyNotNull(mod, editModifiers.value());
      Fix fix;
      switch (editModifiers.kind()) {
        case ADD:
          fix = SuggestedFixes.addModifiers(tree, state, mod);
          break;
        case REMOVE:
          fix = SuggestedFixes.removeModifiers(tree, state, mod);
          break;
        default:
          throw new AssertionError(editModifiers.kind());
      }
      return describeMatch(tree, fix);
    }
  }

  @Test
  public void addModifiers() {
    CompilationTestHelper.newInstance(EditModifiersChecker.class, getClass())
        .addSourceLines(
            "Test.java",
            String.format("import %s;", EditModifiers.class.getCanonicalName()),
            "import javax.annotation.Nullable;",
            "@EditModifiers(value=\"final\", kind=EditModifiers.EditKind.ADD)",
            "class Test {",
            "  // BUG: Diagnostic contains: final Object one",
            "  Object one;",
            "  // BUG: Diagnostic contains: @Nullable final Object two",
            "  @Nullable Object two;",
            "  // BUG: Diagnostic contains: @Nullable public final Object three",
            "  @Nullable public Object three;",
            "  // BUG: Diagnostic contains: public final Object four",
            "  public Object four;",
            "}")
        .doTest();
  }

  @Test
  public void addModifiersComment() {
    CompilationTestHelper.newInstance(EditModifiersChecker.class, getClass())
        .addSourceLines(
            "Test.java",
            String.format("import %s;", EditModifiers.class.getCanonicalName()),
            "import javax.annotation.Nullable;",
            "@EditModifiers(value=\"final\", kind=EditModifiers.EditKind.ADD)",
            "class Test {",
            "  // BUG: Diagnostic contains:"
                + " private @Deprecated /*comment*/ final volatile Object one;",
            "  private @Deprecated /*comment*/ volatile Object one;",
            "  // BUG: Diagnostic contains:"
                + " private @Deprecated /*comment*/ static final Object two = null;",
            "  private @Deprecated /*comment*/ static Object two = null;",
            "}")
        .doTest();
  }

  @Test
  public void addModifiersFirst() {
    CompilationTestHelper.newInstance(EditModifiersChecker.class, getClass())
        .addSourceLines(
            "Test.java",
            String.format("import %s;", EditModifiers.class.getCanonicalName()),
            "import javax.annotation.Nullable;",
            "@EditModifiers(value=\"public\", kind=EditModifiers.EditKind.ADD)",
            "class Test {",
            "  // BUG: Diagnostic contains: public static final transient Object one",
            "  static final transient Object one = null;",
            "}")
        .doTest();
  }

  @Test
  public void removeModifiers() {
    CompilationTestHelper.newInstance(EditModifiersChecker.class, getClass())
        .addSourceLines(
            "Test.java",
            String.format("import %s;", EditModifiers.class.getCanonicalName()),
            "import javax.annotation.Nullable;",
            "@EditModifiers(value=\"final\", kind=EditModifiers.EditKind.REMOVE)",
            "class Test {",
            "  // BUG: Diagnostic contains: Object one",
            "  final Object one = null;",
            "  // BUG: Diagnostic contains: @Nullable Object two",
            "  @Nullable final Object two = null;",
            "  // BUG: Diagnostic contains: @Nullable public Object three",
            "  @Nullable public final Object three = null;",
            "  // BUG: Diagnostic contains: public Object four",
            "  public final Object four = null;",
            "}")
        .doTest();
  }

  @BugPattern(
    category = Category.ONE_OFF,
    
    name = "CastReturn",
    severity = SeverityLevel.ERROR,
    summary = "Adds casts to returned expressions"
  )
  public static class CastReturn extends BugChecker implements ReturnTreeMatcher {

    @Override
    public Description matchReturn(ReturnTree tree, VisitorState state) {
      if (tree.getExpression() == null) {
        return Description.NO_MATCH;
      }
      Type type =
          ASTHelpers.getSymbol(ASTHelpers.findEnclosingNode(state.getPath(), MethodTree.class))
              .getReturnType();
      SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
      String qualifiedTargetType = SuggestedFixes.qualifyType(state, fixBuilder, type.tsym);
      fixBuilder.prefixWith(tree.getExpression(), String.format("(%s) ", qualifiedTargetType));
      return describeMatch(tree, fixBuilder.build());
    }
  }

  @BugPattern(
    category = Category.ONE_OFF,
    
    name = "CastReturn",
    severity = SeverityLevel.ERROR,
    summary = "Adds casts to returned expressions"
  )
  public static class CastReturnFullType extends BugChecker implements ReturnTreeMatcher {

    @Override
    public Description matchReturn(ReturnTree tree, VisitorState state) {
      if (tree.getExpression() == null) {
        return Description.NO_MATCH;
      }
      Type type =
          ASTHelpers.getSymbol(ASTHelpers.findEnclosingNode(state.getPath(), MethodTree.class))
              .getReturnType();
      SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
      String qualifiedTargetType = SuggestedFixes.qualifyType(state, fixBuilder, type);
      fixBuilder.prefixWith(tree.getExpression(), String.format("(%s) ", qualifiedTargetType));
      return describeMatch(tree, fixBuilder.build());
    }
  }

  @Test
  public void qualifiedName_Object() {
    CompilationTestHelper.newInstance(CastReturn.class, getClass())
        .addSourceLines(
            "Test.java",
            "class Test {",
            "  Object f() {",
            "    // BUG: Diagnostic contains: return (Object) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void qualifiedName_imported() {
    CompilationTestHelper.newInstance(CastReturn.class, getClass())
        .addSourceLines(
            "Test.java",
            "import java.util.Map.Entry;",
            "class Test {",
            "  java.util.Map.Entry<String, Integer> f() {",
            "    // BUG: Diagnostic contains: return (Entry) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void qualifiedName_notImported() {
    CompilationTestHelper.newInstance(CastReturn.class, getClass())
        .addSourceLines(
            "Test.java",
            "class Test {",
            "  java.util.Map.Entry<String, Integer> f() {",
            "    // BUG: Diagnostic contains: return (Map.Entry) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void qualifiedName_typeVariable() {
    CompilationTestHelper.newInstance(CastReturn.class, getClass())
        .addSourceLines(
            "Test.java",
            "class Test<T> {",
            "  T f() {",
            "    // BUG: Diagnostic contains: return (T) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fullQualifiedName_Object() {
    CompilationTestHelper.newInstance(CastReturnFullType.class, getClass())
        .addSourceLines(
            "Test.java",
            "class Test {",
            "  Object f() {",
            "    // BUG: Diagnostic contains: return (Object) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fullQualifiedName_imported() {
    CompilationTestHelper.newInstance(CastReturnFullType.class, getClass())
        .addSourceLines(
            "Test.java",
            "import java.util.Map.Entry;",
            "class Test {",
            "  java.util.Map.Entry<String, Integer> f() {",
            "    // BUG: Diagnostic contains: return (Entry<String,Integer>) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fullQualifiedName_notImported() {
    CompilationTestHelper.newInstance(CastReturnFullType.class, getClass())
        .addSourceLines(
            "Test.java",
            "class Test {",
            "  java.util.Map.Entry<String, Integer> f() {",
            "    // BUG: Diagnostic contains: return (Map.Entry<String,Integer>) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void fullQualifiedName_typeVariable() {
    CompilationTestHelper.newInstance(CastReturnFullType.class, getClass())
        .addSourceLines(
            "Test.java",
            "class Test<T> {",
            "  T f() {",
            "    // BUG: Diagnostic contains: return (T) null;",
            "    return null;",
            "  }",
            "}")
        .doTest();
  }

  /** A test check that qualifies javadoc link. */
  @BugPattern(
    name = "JavadocQualifier",
    category = BugPattern.Category.JDK,
    summary = "all javadoc links should be qualified",
    severity = ERROR,
    maturity = MATURE
  )
  public static class JavadocQualifier extends BugChecker implements BugChecker.ClassTreeMatcher {
    @Override
    public Description matchClass(ClassTree tree, final VisitorState state) {
      final DCTree.DCDocComment comment =
          ((JCTree.JCCompilationUnit) state.getPath().getCompilationUnit())
              .docComments.getCommentTree((JCTree) tree);
      if (comment == null) {
        return Description.NO_MATCH;
      }
      final SuggestedFix.Builder fix = SuggestedFix.builder();
      new DocTreePathScanner<Void, Void>() {
        @Override
        public Void visitLink(LinkTree node, Void aVoid) {
          SuggestedFixes.qualifyDocReference(
              fix, new DocTreePath(getCurrentPath(), node.getReference()), state);
          return null;
        }
      }.scan(new DocTreePath(state.getPath(), comment), null);
      if (fix.isEmpty()) {
        return Description.NO_MATCH;
      }
      return describeMatch(tree, fix.build());
    }
  }

  @Test
  public void qualifyJavadocTest() throws Exception {
    BugCheckerRefactoringTestHelper.newInstance(new JavadocQualifier(), getClass())
        .addInputLines(
            "in/Test.java", //
            "import java.util.List;",
            "import java.util.Map;",
            "/** foo {@link List} bar {@link Map#containsKey(Object)} baz {@link #foo} */",
            "class Test {",
            "  void foo() {}",
            "}")
        .addOutputLines(
            "out/Test.java", //
            "import java.util.List;",
            "import java.util.Map;",
            "/** foo {@link java.util.List} bar {@link java.util.Map#containsKey(Object)} baz"
                + " {@link Test#foo} */",
            "class Test {",
            "  void foo() {}",
            "}")
        .doTest(TEXT_MATCH);
  }
}
