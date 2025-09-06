package com.example.comicbooktts

import com.example.comicbooktts.utils.*
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Comprehensive test suite for ComicTTS application
 * 
 * This test suite includes:
 * - Unit tests for utility classes
 * - Integration tests for main activities
 * - Performance and stress tests
 * - Error handling and edge case tests
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Utility Tests
    TextRecognitionHelperTest::class,
    HybridTTSTest::class,
    ComicArchiveExtractorTest::class,
    PreferenceManagerTest::class,
    
    // Activity Tests
    ReaderActivityTest::class,
    
    // Performance Tests
    PerformanceTestSuite::class,
    
    // Integration Tests
    IntegrationTestSuite::class
)
class TestSuite

/**
 * Performance-focused test suite
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    TextRecognitionHelperTest::class,
    HybridTTSTest::class,
    ComicArchiveExtractorTest::class
)
class PerformanceTestSuite

/**
 * Integration test suite for testing component interactions
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ReaderActivityTest::class,
    PreferenceManagerTest::class
)
class IntegrationTestSuite

/**
 * Utility test suite for testing helper classes
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    TextRecognitionHelperTest::class,
    HybridTTSTest::class,
    ComicArchiveExtractorTest::class,
    PreferenceManagerTest::class
)
class UtilityTestSuite

/**
 * Fast test suite for quick validation during development
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    PreferenceManagerTest::class
)
class FastTestSuite

/**
 * Comprehensive test suite for CI/CD pipeline
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // All utility tests
    TextRecognitionHelperTest::class,
    HybridTTSTest::class,
    ComicArchiveExtractorTest::class,
    PreferenceManagerTest::class,
    
    // All activity tests
    ReaderActivityTest::class
)
class CITestSuite
