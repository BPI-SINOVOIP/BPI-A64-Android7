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
	"fmt"

	"github.com/google/blueprint"
)

// AndroidPackageContext is a wrapper for blueprint.PackageContext that adds
// some android-specific helper functions.
type AndroidPackageContext struct {
	blueprint.PackageContext
}

func NewPackageContext(pkgPath string) AndroidPackageContext {
	return AndroidPackageContext{blueprint.NewPackageContext(pkgPath)}
}

// configErrorWrapper can be used with Path functions when a Context is not
// available. A Config can be provided, and errors are stored as a list for
// later retrieval.
//
// The most common use here will be with VariableFunc, where only a config is
// provided, and an error should be returned.
type configErrorWrapper struct {
	pctx   AndroidPackageContext
	config Config
	errors []error
}

var _ PathContext = &configErrorWrapper{}
var _ errorfContext = &configErrorWrapper{}

func (e *configErrorWrapper) Config() interface{} {
	return e.config
}
func (e *configErrorWrapper) Errorf(format string, args ...interface{}) {
	e.errors = append(e.errors, fmt.Errorf(format, args...))
}
func (e *configErrorWrapper) AddNinjaFileDeps(deps ...string) {
	e.pctx.AddNinjaFileDeps(deps...)
}

// SourcePathVariable returns a Variable whose value is the source directory
// appended with the supplied path. It may only be called during a Go package's
// initialization - either from the init() function or as part of a
// package-scoped variable's initialization.
func (p AndroidPackageContext) SourcePathVariable(name, path string) blueprint.Variable {
	return p.VariableFunc(name, func(config interface{}) (string, error) {
		ctx := &configErrorWrapper{p, config.(Config), []error{}}
		p := safePathForSource(ctx, path)
		if len(ctx.errors) > 0 {
			return "", ctx.errors[0]
		}
		return p.String(), nil
	})
}

// HostBinVariable returns a Variable whose value is the path to a host tool
// in the bin directory for host targets. It may only be called during a Go
// package's initialization - either from the init() function or as part of a
// package-scoped variable's initialization.
func (p AndroidPackageContext) HostBinToolVariable(name, path string) blueprint.Variable {
	return p.VariableFunc(name, func(config interface{}) (string, error) {
		ctx := &configErrorWrapper{p, config.(Config), []error{}}
		p := PathForOutput(ctx, "host", ctx.config.PrebuiltOS(), "bin", path)
		if len(ctx.errors) > 0 {
			return "", ctx.errors[0]
		}
		return p.String(), nil
	})
}

// HostJavaToolVariable returns a Variable whose value is the path to a host
// tool in the frameworks directory for host targets. It may only be called
// during a Go package's initialization - either from the init() function or as
// part of a package-scoped variable's initialization.
func (p AndroidPackageContext) HostJavaToolVariable(name, path string) blueprint.Variable {
	return p.VariableFunc(name, func(config interface{}) (string, error) {
		ctx := &configErrorWrapper{p, config.(Config), []error{}}
		p := PathForOutput(ctx, "host", ctx.config.PrebuiltOS(), "framework", path)
		if len(ctx.errors) > 0 {
			return "", ctx.errors[0]
		}
		return p.String(), nil
	})
}

// IntermediatesPathVariable returns a Variable whose value is the intermediate
// directory appended with the supplied path. It may only be called during a Go
// package's initialization - either from the init() function or as part of a
// package-scoped variable's initialization.
func (p AndroidPackageContext) IntermediatesPathVariable(name, path string) blueprint.Variable {
	return p.VariableFunc(name, func(config interface{}) (string, error) {
		ctx := &configErrorWrapper{p, config.(Config), []error{}}
		p := PathForIntermediates(ctx, path)
		if len(ctx.errors) > 0 {
			return "", ctx.errors[0]
		}
		return p.String(), nil
	})
}

// PrefixedPathsForOptionalSourceVariable returns a Variable whose value is the
// list of present source paths prefixed with the supplied prefix. It may only
// be called during a Go package's initialization - either from the init()
// function or as part of a package-scoped variable's initialization.
func (p AndroidPackageContext) PrefixedPathsForOptionalSourceVariable(
	name, prefix string, paths []string) blueprint.Variable {

	return p.VariableFunc(name, func(config interface{}) (string, error) {
		ctx := &configErrorWrapper{p, config.(Config), []error{}}
		paths := PathsForOptionalSource(ctx, "", paths)
		if len(ctx.errors) > 0 {
			return "", ctx.errors[0]
		}
		return JoinWithPrefix(paths.Strings(), prefix), nil
	})
}
