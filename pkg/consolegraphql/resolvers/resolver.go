/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package resolvers

//go:generate go run github.com/99designs/gqlgen generate --config console/console-server/src/main/resources/gqlgen.yml --verbose

import (
	"github.com/enmasseproject/enmasse/pkg/client/clientset/versioned/typed/admin/v1beta2"
	"github.com/enmasseproject/enmasse/pkg/client/clientset/versioned/typed/enmasse/v1beta1"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql/agent"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql/cache"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql/filter"
)

type Resolver struct {
	AdminConfig  *v1beta2.AdminV1beta2Client
	CoreConfig   *v1beta1.EnmasseV1beta1Client
	Cache        *cache.MemdbCache
	GetCollector func(string) agent.Delegate
}

func (r *Resolver) Query() QueryResolver {
	return &queryResolver{r}
}

func (r *Resolver) Mutation() MutationResolver {
	return &mutationResolver{r}
}

type queryResolver struct{ *Resolver }

type mutationResolver struct{ *Resolver }

func CalcLowerUpper(offset *int, first *int, len int) (int, int) {
	lower := 0
	if offset != nil && *offset > 0 {
		lower = Min(*offset, len)
	}
	upper := len
	if first != nil && *first > 0 {
		upper = Min(lower+*first, len)
	}
	return lower, upper
}

func Min(x, y int) int {
	if x < y {
		return x
	}
	return y
}

func BuildFilter(f *string) (cache.ObjectFilter, error) {
	if f != nil && *f != "" {
		expression, err := filter.ParseFilterExpression(*f)
		if err != nil {
			return nil, err
		}
		return func(i interface{}) (match bool, cont bool, e error) {
			rv, e := expression.Eval(i)
			if e != nil {
				return false, false, e
			}
			return rv.(bool), true, nil
		}, nil
	}
	return nil, nil
}

func BuildOrderer(o *string) (func(interface{}) error, error) {
	if o != nil && *o != "" {
		orderby, err := filter.ParseOrderByExpression(*o)
		if err != nil {
			return nil, err
		}

		return orderby.Sort, err
	}
	return func(interface{}) error { return nil }, nil
}
