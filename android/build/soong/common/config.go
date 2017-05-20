// Copyright 2015 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package common

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"

	"github.com/google/blueprint/proptools"
)

var Bool = proptools.Bool

// The configuration file name
const configFileName = "soong.config"
const productVariablesFileName = "soong.variables"

// A FileConfigurableOptions contains options which can be configured by the
// config file. These will be included in the config struct.
type FileConfigurableOptions struct {
	Mega_device *bool `json:",omitempty"`
}

func (f *FileConfigurableOptions) SetDefaultConfig() {
	*f = FileConfigurableOptions{}
}

type Config struct {
	*config
}

// A config object represents the entire build configuration for Android.
type config struct {
	FileConfigurableOptions
	ProductVariables productVariables

	ConfigFileName           string
	ProductVariablesFileName string

	DeviceArches []Arch
	HostArches   map[HostType][]Arch

	srcDir   string // the path of the root source directory
	buildDir string // the path of the build output directory

	envLock   sync.Mutex
	envDeps   map[string]string
	envFrozen bool

	inMake bool
}

type jsonConfigurable interface {
	SetDefaultConfig()
}

func loadConfig(config *config) error {
	err := loadFromConfigFile(&config.FileConfigurableOptions, config.ConfigFileName)
	if err != nil {
		return err
	}

	return loadFromConfigFile(&config.ProductVariables, config.ProductVariablesFileName)
}

// loads configuration options from a JSON file in the cwd.
func loadFromConfigFile(configurable jsonConfigurable, filename string) error {
	// Try to open the file
	configFileReader, err := os.Open(filename)
	defer configFileReader.Close()
	if os.IsNotExist(err) {
		// Need to create a file, so that blueprint & ninja don't get in
		// a dependency tracking loop.
		// Make a file-configurable-options with defaults, write it out using
		// a json writer.
		configurable.SetDefaultConfig()
		err = saveToConfigFile(configurable, filename)
		if err != nil {
			return err
		}
	} else {
		// Make a decoder for it
		jsonDecoder := json.NewDecoder(configFileReader)
		err = jsonDecoder.Decode(configurable)
		if err != nil {
			return fmt.Errorf("config file: %s did not parse correctly: "+err.Error(), filename)
		}
	}

	// No error
	return nil
}

func saveToConfigFile(config jsonConfigurable, filename string) error {
	data, err := json.MarshalIndent(&config, "", "    ")
	if err != nil {
		return fmt.Errorf("cannot marshal config data: %s", err.Error())
	}

	configFileWriter, err := os.Create(filename)
	if err != nil {
		return fmt.Errorf("cannot create empty config file %s: %s\n", filename, err.Error())
	}
	defer configFileWriter.Close()

	_, err = configFileWriter.Write(data)
	if err != nil {
		return fmt.Errorf("default config file: %s could not be written: %s", filename, err.Error())
	}

	_, err = configFileWriter.WriteString("\n")
	if err != nil {
		return fmt.Errorf("default config file: %s could not be written: %s", filename, err.Error())
	}

	return nil
}

// New creates a new Config object.  The srcDir argument specifies the path to
// the root source directory. It also loads the config file, if found.
func NewConfig(srcDir, buildDir string) (Config, error) {
	// Make a config with default options
	config := Config{
		config: &config{
			ConfigFileName:           filepath.Join(buildDir, configFileName),
			ProductVariablesFileName: filepath.Join(buildDir, productVariablesFileName),

			srcDir:   srcDir,
			buildDir: buildDir,
			envDeps:  make(map[string]string),
		},
	}

	// Sanity check the build and source directories. This won't catch strange
	// configurations with symlinks, but at least checks the obvious cases.
	absBuildDir, err := filepath.Abs(buildDir)
	if err != nil {
		return Config{}, err
	}

	absSrcDir, err := filepath.Abs(srcDir)
	if err != nil {
		return Config{}, err
	}

	if strings.HasPrefix(absSrcDir, absBuildDir) {
		return Config{}, fmt.Errorf("Build dir must not contain source directory")
	}

	// Load any configurable options from the configuration file
	err = loadConfig(config.config)
	if err != nil {
		return Config{}, err
	}

	inMakeFile := filepath.Join(buildDir, ".soong.in_make")
	if _, err := os.Stat(inMakeFile); err == nil {
		config.inMake = true
	}

	hostArches, deviceArches, err := decodeArchProductVariables(config.ProductVariables)
	if err != nil {
		return Config{}, err
	}

	if Bool(config.Mega_device) {
		deviceArches, err = decodeMegaDevice()
		if err != nil {
			return Config{}, err
		}
	}

	config.HostArches = hostArches
	config.DeviceArches = deviceArches

	return config, nil
}

func (c *config) RemoveAbandonedFiles() bool {
	return false
}

// PrebuiltOS returns the name of the host OS used in prebuilts directories
func (c *config) PrebuiltOS() string {
	switch runtime.GOOS {
	case "linux":
		return "linux-x86"
	case "darwin":
		return "darwin-x86"
	default:
		panic("Unknown GOOS")
	}
}

// GoRoot returns the path to the root directory of the Go toolchain.
func (c *config) GoRoot() string {
	return fmt.Sprintf("%s/prebuilts/go/%s", c.srcDir, c.PrebuiltOS())
}

func (c *config) CpPreserveSymlinksFlags() string {
	switch runtime.GOOS {
	case "darwin":
		return "-R"
	case "linux":
		return "-d"
	default:
		return ""
	}
}

func (c *config) Getenv(key string) string {
	var val string
	var exists bool
	c.envLock.Lock()
	if val, exists = c.envDeps[key]; !exists {
		if c.envFrozen {
			panic("Cannot access new environment variables after envdeps are frozen")
		}
		val = os.Getenv(key)
		c.envDeps[key] = val
	}
	c.envLock.Unlock()
	return val
}

func (c *config) EnvDeps() map[string]string {
	c.envLock.Lock()
	c.envFrozen = true
	c.envLock.Unlock()
	return c.envDeps
}

func (c *config) EmbeddedInMake() bool {
	return c.inMake
}

// DeviceName returns the name of the current device target
// TODO: take an AndroidModuleContext to select the device name for multi-device builds
func (c *config) DeviceName() string {
	return *c.ProductVariables.DeviceName
}

func (c *config) DeviceUsesClang() bool {
	if c.ProductVariables.DeviceUsesClang != nil {
		return *c.ProductVariables.DeviceUsesClang
	}
	return true
}

func (c *config) ResourceOverlays() []SourcePath {
	return nil
}

func (c *config) PlatformVersion() string {
	return "M"
}

func (c *config) PlatformSdkVersion() string {
	return "22"
}

func (c *config) BuildNumber() string {
	return "000000"
}

func (c *config) ProductAaptConfig() []string {
	return []string{"normal", "large", "xlarge", "hdpi", "xhdpi", "xxhdpi"}
}

func (c *config) ProductAaptPreferredConfig() string {
	return "xhdpi"
}

func (c *config) ProductAaptCharacteristics() string {
	return "nosdcard"
}

func (c *config) DefaultAppCertificateDir(ctx PathContext) SourcePath {
	return PathForSource(ctx, "build/target/product/security")
}

func (c *config) DefaultAppCertificate(ctx PathContext) SourcePath {
	return c.DefaultAppCertificateDir(ctx).Join(ctx, "testkey")
}

func (c *config) AllowMissingDependencies() bool {
	return Bool(c.ProductVariables.Allow_missing_dependencies)
}

func (c *config) SkipDeviceInstall() bool {
	return c.EmbeddedInMake() || Bool(c.Mega_device)
}
