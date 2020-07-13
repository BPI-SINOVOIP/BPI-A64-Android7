"""Test result object"""
import unittest

class EmuTestResult(unittest.TextTestResult):
    """Holder for test result information.

    Test results are automatically managed by the TestCase and TestSuite
    classes, and do not need to be explicitly manipulated by writers of tests.

    Each instance holds the total number of tests run, and collections of
    failures and errors that occurred among those test runs. The collections
    contain tuples of (testcase, exceptioninfo), where exceptioninfo is the
    formatted traceback of the error that occurred.
    """

    def __init__(self, stream, descriptions, verbosity):
        unittest.TextTestResult.__init__(self, stream, descriptions, verbosity)
        self.passes = []
        self.timeout = []

    def addSuccess(self, test):
        "Called when a test has completed successfully"
        self.passes.append(test)
        pass

    def addError(self, test, err):
        """Called when an expected failure/error occured."""
        self.errors.append((test, self._exc_info_to_string(err, test)))

class EmuTextTestRunner(unittest.TextTestRunner):
    def _makeResult(self):
        return EmuTestResult(self.stream, self.descriptions, self.verbosity)
