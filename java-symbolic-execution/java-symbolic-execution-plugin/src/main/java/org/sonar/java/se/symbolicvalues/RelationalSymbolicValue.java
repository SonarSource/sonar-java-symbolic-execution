/*
 * SonarQube Java
 * Copyright (C) 2012-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.java.se.symbolicvalues;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.java.Preconditions;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintsByDomain;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.plugins.java.api.semantic.Symbol;

import static org.sonar.java.se.symbolicvalues.RelationalSymbolicValue.Kind.EQUAL;
import static org.sonar.java.se.symbolicvalues.RelationalSymbolicValue.Kind.GREATER_THAN_OR_EQUAL;
import static org.sonar.java.se.symbolicvalues.RelationalSymbolicValue.Kind.LESS_THAN;
import static org.sonar.java.se.symbolicvalues.RelationalSymbolicValue.Kind.METHOD_EQUALS;

public class RelationalSymbolicValue extends BinarySymbolicValue {

  private static final int MAX_ITERATIONS = 10_000;
  private static final int MAX_DEDUCED_RELATIONS = 100_000;

  public enum Kind {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    METHOD_EQUALS(".EQ."),
    NOT_METHOD_EQUALS(".NE.");

    final String operand;

    Kind(String operand) {
      this.operand = operand;
    }

    Kind inverse() {
      switch (this) {
        case EQUAL:
          return NOT_EQUAL;
        case NOT_EQUAL:
          return EQUAL;
        case GREATER_THAN_OR_EQUAL:
          return LESS_THAN;
        case LESS_THAN:
          return GREATER_THAN_OR_EQUAL;
        case METHOD_EQUALS:
          return NOT_METHOD_EQUALS;
        case NOT_METHOD_EQUALS:
          return METHOD_EQUALS;
        default:
          throw new IllegalStateException("Unsupported relation!");
      }
    }

  }

  final Kind kind;
  private int hashCode;

  public RelationalSymbolicValue(Kind kind) {
    this.kind = kind;
  }

  // VisibleForTesting
  RelationalSymbolicValue(Kind kind, SymbolicValue leftOp, SymbolicValue rightOp) {
    this.kind = kind;
    this.leftOp = leftOp;
    this.rightOp = rightOp;
  }

  @Override
  public List<ProgramState> setConstraint(ProgramState initialProgramState, BooleanConstraint booleanConstraint) {
    return setConstraint(initialProgramState, booleanConstraint, new HashSet<>(initialProgramState.knownRelations()));
  }

  @Override
  protected List<ProgramState> setConstraint(ProgramState initialProgramState, Constraint constraint, Set<RelationalSymbolicValue> knownRelations) {
    if (constraint == BooleanConstraint.FALSE) {
      return inverse().setConstraint(initialProgramState, BooleanConstraint.TRUE, knownRelations);
    }
    if (constraint != BooleanConstraint.TRUE) {
      // not a boolean constraint
      return setConstraint(initialProgramState, constraint);
    }
    if (knownRelations.contains(this)) {
      return Collections.singletonList(initialProgramState);
    }
    Set<RelationalSymbolicValue> newRelations = new HashSet<>();
    newRelations.add(this);
    newRelations.addAll(transitiveRelations(knownRelations));

    boolean unfulfilled = newRelations.stream()
      .map(r -> r.resolveRelationState(knownRelations))
      .anyMatch(RelationState.UNFULFILLED::equals);

    if (unfulfilled) {
      return Collections.emptyList();
    }
    knownRelations.add(this);
    return getNewProgramStates(initialProgramState, newRelations, knownRelations);
  }

  private static List<ProgramState> getNewProgramStates(ProgramState initialProgramState, Set<RelationalSymbolicValue> newRelations,
                                                        Set<RelationalSymbolicValue> knownRelations) {
    List<ProgramState> programStates = new ArrayList<>();
    programStates.add(initialProgramState);
    for (RelationalSymbolicValue relationalSymbolicValue : newRelations) {
      List<ProgramState> intermediateStates = new ArrayList<>();
      for (ProgramState programState: programStates) {
        intermediateStates.addAll(relationalSymbolicValue.copyAllConstraints(programState, knownRelations));
      }
      programStates = intermediateStates;
    }
    return programStates;
  }

  RelationalSymbolicValue inverse() {
    return new RelationalSymbolicValue(kind.inverse(), leftOp, rightOp);
  }

  private List<ProgramState> copyAllConstraints(ProgramState initialState, Set<RelationalSymbolicValue> knownRelations) {
    ProgramState programState = initialState;
    if (programState.canReach(leftOp) || programState.canReach(rightOp)) {
      programState = programState.addConstraint(this, BooleanConstraint.TRUE);
    }
    List<ProgramState> results = new ArrayList<>();
    List<ProgramState> copiedConstraints = copyConstraintFromTo(leftOp, rightOp, programState, knownRelations);
    for (ProgramState ps : copiedConstraints) {
      List<ProgramState> copiedConstraintsRightToLeft = copyConstraintFromTo(rightOp, leftOp, ps, knownRelations);
      if (isEqualityCheck() && hasBooleanSymbolicValueAsOperand()) {
        copiedConstraintsRightToLeft = addNullConstraintsForBooleanWrapper(programState, copiedConstraints);
      }
      if (copiedConstraintsRightToLeft.size() == 1 && copiedConstraintsRightToLeft.get(0).equals(programState)) {
        results.add(programState.addConstraint(this, BooleanConstraint.TRUE));
      } else {
        results.addAll(copiedConstraintsRightToLeft);
      }
    }
    return results;
  }

  private boolean hasBooleanSymbolicValueAsOperand() {
    return (leftOp instanceof BooleanSymbolicValue) || (rightOp instanceof BooleanSymbolicValue);
  }

  private boolean isEqualityCheck() {
    return isEquality() || isNonEquality();
  }

  private List<ProgramState> addNullConstraintsForBooleanWrapper(ProgramState initialProgramState, List<ProgramState> copiedConstraints) {
    BooleanConstraint leftConstraint = initialProgramState.getConstraint(leftOp, BooleanConstraint.class);
    BooleanConstraint rightConstraint = initialProgramState.getConstraint(rightOp, BooleanConstraint.class);
    
    if (shouldAddNullConstraint(leftConstraint, rightConstraint)) {
      return getProgramStatesWithNullConstraints(copiedConstraints, leftOp);
    }
    
    if (shouldAddNullConstraint(rightConstraint, leftConstraint)) {
      return getProgramStatesWithNullConstraints(copiedConstraints, rightOp);
    }
    return copiedConstraints;
  }

  private boolean shouldAddNullConstraint(@Nullable BooleanConstraint constraint1, @Nullable BooleanConstraint constraint2) {
    return !isEquality() && constraint1 == null && constraint2 != null;
  }

  private static List<ProgramState> getProgramStatesWithNullConstraints(List<ProgramState> copiedConstraints, SymbolicValue operand) {
    List<ProgramState> nullConstraints = copiedConstraints.stream()
      .flatMap(ps -> operand.setConstraint(ps, ObjectConstraint.NULL).stream())
      .map(ps -> ps.removeConstraintsOnDomain(operand, BooleanConstraint.class))
      .toList();
    return Stream.of(copiedConstraints, nullConstraints).flatMap(List::stream).toList();
  }

  private List<ProgramState> copyConstraintFromTo(SymbolicValue from, SymbolicValue to, ProgramState programState, Set<RelationalSymbolicValue> knownRelations) {
    List<ProgramState> states = new ArrayList<>();
    states.add(programState);
    ConstraintsByDomain leftConstraints = programState.getConstraints(from);
    if (leftConstraints == null) {
      ConstraintsByDomain rightConstraints = programState.getConstraints(to);
      if (rightConstraints != null) {
        applyConstraints(from, knownRelations, states, rightConstraints);
      }
      return states;
    }
    applyConstraints(to, knownRelations, states, leftConstraints);
    return states;
  }

  private void applyConstraints(SymbolicValue symbolicValue, Set<RelationalSymbolicValue> knownRelations, List<ProgramState> states, ConstraintsByDomain constraints) {
    constraints.forEach((d, c) -> {
      Constraint constraint = c.copyOver(kind);
      if (constraint != null) {
        List<ProgramState> newStates = applyConstraint(constraint, symbolicValue, states, knownRelations);
        states.clear();
        states.addAll(newStates);
      }
    });
  }

  private static List<ProgramState> applyConstraint(Constraint constraint, SymbolicValue to, List<ProgramState> states, Set<RelationalSymbolicValue> knownRelations) {
    List<ProgramState> newStates = new ArrayList<>();
    states.forEach(state -> newStates.addAll(to.setConstraint(state, constraint, knownRelations)));
    return newStates;
  }

  // VisibleForTesting
  RelationState resolveRelationState(Set<RelationalSymbolicValue> knownRelations) {
    if (hasSameOperand()) {
      return relationStateForSameOperand();
    }

    return knownRelations.stream()
      .map(r -> r.implies(this))
      .filter(RelationState::isDetermined)
      .findAny().orElse(RelationState.UNDETERMINED);
  }

  private RelationState relationStateForSameOperand() {
    switch (kind) {
      case EQUAL,
        GREATER_THAN_OR_EQUAL,
        METHOD_EQUALS:
        return RelationState.FULFILLED;
      case NOT_EQUAL,
        LESS_THAN,
        NOT_METHOD_EQUALS:
        return RelationState.UNFULFILLED;
      default:
        throw new IllegalStateException("Unknown resolution for same operand " + this);
    }
  }

  private RelationState implies(RelationalSymbolicValue relation) {
    if (this.equals(relation)) {
      return RelationState.FULFILLED;
    }
    if (inverse().equals(relation)) {
      return RelationState.UNFULFILLED;
    }
    if (hasSameOperandsAs(relation)) {
      return RelationStateTable.solveRelation(kind, relation.kind);
    }
    return RelationState.UNDETERMINED;
  }

  // VisibleForTesting
  Set<RelationalSymbolicValue> transitiveRelations(Set<RelationalSymbolicValue> knownRelations) {
    Set<RelationalSymbolicValue> newRelations = new HashSet<>();
    Deque<RelationalSymbolicValue> workList = new ArrayDeque<>();
    int iterations = 0;
    workList.add(this);
    while (!workList.isEmpty()) {
      int relationSize = newRelations.size() * knownRelations.size();
      if (relationSize > MAX_DEDUCED_RELATIONS || iterations > MAX_ITERATIONS) {
        // safety mechanism in case of an error in the algorithm
        throw new RelationalSymbolicValue.TransitiveRelationExceededException("Used relations: " + relationSize + ". Iterations " + iterations);
      }
      iterations++;
      RelationalSymbolicValue relation = workList.pop();
      for (RelationalSymbolicValue knownRelation : knownRelations) {
        RelationalSymbolicValue r = relation.deduceTransitiveOrSimplified(knownRelation);
        if (r != null && !knownRelations.contains(r) && newRelations.add(r)) {
          workList.add(r);
        }
      }
    }
    return newRelations;
  }

  // VisibleForTesting
  RelationalSymbolicValue deduceTransitiveOrSimplified(RelationalSymbolicValue other) {
    RelationalSymbolicValue result = simplify(other);
    if (result != null) {
      return result;
    }
    return combineTransitively(other);
  }

  @CheckForNull
  private RelationalSymbolicValue simplify(RelationalSymbolicValue other) {
    // a >= b && b >= a -> a == b
    if (kind == GREATER_THAN_OR_EQUAL && other.kind == GREATER_THAN_OR_EQUAL
      && hasSameOperandsAs(other) && !equals(other)) {
      return new RelationalSymbolicValue(EQUAL, leftOp, rightOp);
    }
    return null;
  }

  // VisibleForTesting
  boolean potentiallyTransitiveWith(RelationalSymbolicValue other) {
    if (hasSameOperand() || other.hasSameOperand()) {
      return false;
    }
    return (hasOperand(other.leftOp) || hasOperand(other.rightOp)) && !hasSameOperandsAs(other);
  }

  @CheckForNull
  private RelationalSymbolicValue combineTransitively(RelationalSymbolicValue other) {
    if (!potentiallyTransitiveWith(other)) {
      return null;
    }
    RelationalSymbolicValue transitive = combineTransitivelyOneWay(other);
    if (transitive != null) {
      return transitive;
    }
    return other.combineTransitivelyOneWay(this);
  }

  @CheckForNull
  private RelationalSymbolicValue combineTransitivelyOneWay(RelationalSymbolicValue other) {
    RelationalSymbolicValue transitive = equalityTransitiveBuilder(other);
    if (transitive != null) {
      return transitive;
    }
    transitive = lessThanTransitiveBuilder(other);
    if (transitive != null) {
      return transitive;
    }
    return greaterThanEqualTransitiveBuilder(other);
  }

  @CheckForNull
  private RelationalSymbolicValue equalityTransitiveBuilder(RelationalSymbolicValue other) {
    if (!isEquality()
      || (kind == METHOD_EQUALS && other.kind == EQUAL)) {
      return null;
    }

    return new RelationalSymbolicValue(other.kind,
      hasOperand(other.leftOp) ? differentOperand(other) : other.leftOp,
      hasOperand(other.leftOp) ? other.rightOp : differentOperand(other));
  }

  @CheckForNull
  private RelationalSymbolicValue lessThanTransitiveBuilder(RelationalSymbolicValue other) {
    if (kind != LESS_THAN) {
      return null;
    }
    if (other.kind == LESS_THAN) {
      // a < x && x < b => a < b
      if (rightOp.equals(other.leftOp)) {
        return new RelationalSymbolicValue(LESS_THAN, leftOp, other.rightOp);
      }
      // x < a && b < x => b < a
      if (leftOp.equals(other.rightOp)) {
        return new RelationalSymbolicValue(LESS_THAN, other.leftOp, rightOp);
      }
    }
    if (other.kind == GREATER_THAN_OR_EQUAL) {
      // a < x && b >= x => a < b
      if (rightOp.equals(other.rightOp)) {
        return new RelationalSymbolicValue(LESS_THAN, leftOp, other.leftOp);
      }
      // x < a && x >= b => b < a
      if (leftOp.equals(other.leftOp)) {
        return new RelationalSymbolicValue(LESS_THAN, other.rightOp, rightOp);
      }
    }
    return null;
  }

  @CheckForNull
  private RelationalSymbolicValue greaterThanEqualTransitiveBuilder(RelationalSymbolicValue other) {
    // a >= x && x >= b -> a >= b
    if (kind == GREATER_THAN_OR_EQUAL && other.kind == GREATER_THAN_OR_EQUAL && rightOp.equals(other.leftOp)) {
      return new RelationalSymbolicValue(GREATER_THAN_OR_EQUAL, leftOp, other.rightOp);
    }
    return null;
  }

  private boolean hasSameOperand() {
    return leftOp.equals(rightOp);
  }

  private boolean hasOperand(SymbolicValue operand) {
    return leftOp.equals(operand) || rightOp.equals(operand);
  }

  private boolean hasSameOperandsAs(RelationalSymbolicValue other) {
    return (leftOp.equals(other.leftOp) && rightOp.equals(other.rightOp))
      || (leftOp.equals(other.rightOp) && rightOp.equals(other.leftOp));
  }

  // VisibleForTesting
  SymbolicValue differentOperand(RelationalSymbolicValue other) {
    Preconditions.checkState(potentiallyTransitiveWith(other), "%s is not in transitive relationship with %s", this, other);
    return other.hasOperand(leftOp) ? rightOp : leftOp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RelationalSymbolicValue that = (RelationalSymbolicValue) o;
    if (kind != that.kind) {
      return false;
    }
    if (leftOp.equals(that.leftOp) && rightOp.equals(that.rightOp)) {
      return true;
    }
    return isCommutative() && leftOp.equals(that.rightOp) && rightOp.equals(that.leftOp);
  }

  private boolean isCommutative() {
    switch (kind) {
      case EQUAL,
        NOT_EQUAL,
        METHOD_EQUALS,
        NOT_METHOD_EQUALS:
        return true;
      default:
        return false;
    }
  }

  public boolean isEquality() {
    return kind == Kind.EQUAL || kind == Kind.METHOD_EQUALS;
  }

  private boolean isNonEquality() {
    return Kind.NOT_EQUAL == kind || Kind.NOT_METHOD_EQUALS == kind;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      // hashCode doesn't depend on order of operands, to make commutative operators equal when operands are swapped
      hashCode = kind.hashCode() + leftOp.hashCode() + rightOp.hashCode();
    }
    return hashCode;
  }

  @Override
  public String toString() {
    return leftOp.toString() + symbolToString(leftSymbol) + kind.operand + rightOp.toString() + symbolToString(rightSymbol);
  }

  private static String symbolToString(@Nullable Symbol symbol) {
    return symbol != null ? ("(" + symbol.toString() + ")") : "";
  }

  public static class TransitiveRelationExceededException extends RuntimeException {
    public TransitiveRelationExceededException(String msg) {
      super("Number of transitive relations exceeded!" + msg);
    }
  }

  // VisibleForTesting
  public Kind kind() {
    return kind;
  }

}
