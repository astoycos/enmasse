/*
 * Copyright 2018-2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by informer-gen. DO NOT EDIT.

package v1beta1

import (
	internalinterfaces "github.com/enmasseproject/enmasse/pkg/client/informers/externalversions/internalinterfaces"
)

// Interface provides access to all the informers in this group version.
type Interface interface {
	// Addresses returns a AddressInformer.
	Addresses() AddressInformer
	// AddressSpaces returns a AddressSpaceInformer.
	AddressSpaces() AddressSpaceInformer
	// AddressSpaceSchemas returns a AddressSpaceSchemaInformer.
	AddressSpaceSchemas() AddressSpaceSchemaInformer
	// AuthenticationServices returns a AuthenticationServiceInformer.
	AuthenticationServices() AuthenticationServiceInformer
}

type version struct {
	factory          internalinterfaces.SharedInformerFactory
	namespace        string
	tweakListOptions internalinterfaces.TweakListOptionsFunc
}

// New returns a new Interface.
func New(f internalinterfaces.SharedInformerFactory, namespace string, tweakListOptions internalinterfaces.TweakListOptionsFunc) Interface {
	return &version{factory: f, namespace: namespace, tweakListOptions: tweakListOptions}
}

// Addresses returns a AddressInformer.
func (v *version) Addresses() AddressInformer {
	return &addressInformer{factory: v.factory, namespace: v.namespace, tweakListOptions: v.tweakListOptions}
}

// AddressSpaces returns a AddressSpaceInformer.
func (v *version) AddressSpaces() AddressSpaceInformer {
	return &addressSpaceInformer{factory: v.factory, namespace: v.namespace, tweakListOptions: v.tweakListOptions}
}

// AddressSpaceSchemas returns a AddressSpaceSchemaInformer.
func (v *version) AddressSpaceSchemas() AddressSpaceSchemaInformer {
	return &addressSpaceSchemaInformer{factory: v.factory, tweakListOptions: v.tweakListOptions}
}

// AuthenticationServices returns a AuthenticationServiceInformer.
func (v *version) AuthenticationServices() AuthenticationServiceInformer {
	return &authenticationServiceInformer{factory: v.factory, namespace: v.namespace, tweakListOptions: v.tweakListOptions}
}
