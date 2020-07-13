class Error(Exception):
    """Base class for exceptions in this module."""
    pass

class LaunchError(Error):
    """Exception raised for errors in launching emulator

    Attributes:
        avd -- avd which failed to launch
    """

    def __init__(self, avd):
        self.avd = avd

class TimeoutError(Error):
    """Exception raised for timeout

    Attributes:
        cmd -- cmd which timed out
        timeout  -- value of timeout
    """

    def __init__(self, cmd, timeout):
        self.cmd = cmd
        self.timeout = timeout

class SlowBootError(Error):
    """Exception raised for boot time exceeds expected range

    Attributes:
        cmd -- cmd which timed out
        expected_time  -- value of expected boot time
    """

    def __init__(self, cmd, expected_time):
        self.cmd = cmd
        self.expected_time = expected_time

class ConfigError(Error):
    """Exception raised when configuration file is not valid

    Attributes:
    """
    pass
