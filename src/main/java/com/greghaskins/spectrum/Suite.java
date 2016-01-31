package com.greghaskins.spectrum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import com.greghaskins.spectrum.Spectrum.Block;

class Suite extends Runner {

	private final CompositeBlock beforeAll = new CompositeBlock();
	private final CompositeBlock afterAll = new CompositeBlock();

	private final CompositeBlock beforeEach = new CompositeBlock();
	private final CompositeBlock afterEach = new CompositeBlock();

	private final List<Runner> children = new ArrayList<>();

	private final Description description;

	public Suite(final Description description) {
		this.description = description;
	}

	public Suite addSuite(final String name) {
		return addSuite(Description.createSuiteDescription(name));
	}

	public Suite addSuite(final Description description) {
		final Suite suite = new Suite(description);
		suite.beforeAll(this.beforeAll);
		suite.beforeEach(this.beforeEach);
		suite.afterEach(this.afterEach);
		addChild(suite);
		return suite;
	}

	public Spec addSpec(final String name, final Block block) {
		final CompositeBlock specBlockInContext = new CompositeBlock(Arrays.asList(this.beforeAll, this.beforeEach, block, this.afterEach));
		final Description specDescription = Description.createTestDescription(this.description.getClassName(), name);
		final Spec spec = new Spec(specDescription, specBlockInContext);
		addChild(spec);
		return spec;
	}

	private void addChild(final Runner runner) {
		this.description.addChild(runner.getDescription());
		this.children.add(runner);
	}

	public void beforeAll(final Block block) {
		this.beforeAll.addBlock(new RunOnceBlock(block));
	}

	public void afterAll(final Block block) {
		this.afterAll.addBlock(block);
	}

	public void beforeEach(final Block block) {
		this.beforeEach.addBlock(block);
	}

	public void afterEach(final Block block) {
		this.afterEach.addBlock(block);
	}

	@Override
	public void run(final RunNotifier notifier) {
		if (this.testCount() == 0) {
			notifier.fireTestIgnored(this.description);
			runChildren(notifier);
		} else {
			runChildren(notifier);
			runAfterAll(notifier);
		}
	}

	private void runChildren(final RunNotifier notifier) {
		this.children.stream().forEach((child) -> child.run(notifier));
	}

	private void runAfterAll(final RunNotifier notifier) {
		try {
			this.afterAll.run();
		} catch (final Throwable e) {
			final Description failureDescription = Description.createTestDescription(this.description.getClassName(), "error in afterAll");
			this.description.addChild(failureDescription);
			notifier.fireTestFailure(new Failure(failureDescription, e));
		}
	}

	@Override
	public Description getDescription() {
		return this.description;
	}

	@Override
	public int testCount() {
		return this.children.stream().mapToInt((child) -> { return child.testCount(); }).sum();
	}

}
