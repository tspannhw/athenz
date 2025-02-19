// Copyright The Athenz Authors
// Licensed under the terms of the Apache version 2.0 license. See LICENSE file for terms.

package svc

import (
	"github.com/AthenZ/athenz/libs/go/athenz-common/log"
	"github.com/AthenZ/athenz/libs/go/sia/aws/stssession"
	"github.com/AthenZ/athenz/provider/aws/sia-ec2/options"
	"os"
)

type EKSFetcher struct {
}

func (fetcher *EKSFetcher) Fetch(host MsdHost, accountId string) (ServicesData, error) {

	opts, err := options.NewOptions(host.SiaConfig, accountId, "", SIA_DIR, "", "", "", nil, "", nil)
	if err != nil {
		log.Fatalf("Unable to formulate options, error: %v\n", err)
	}

	return ServicesData{
		SrvArr: ec2ToMsdService(opts.Services),
		Domain: opts.Domain,
	}, nil
}

func (fetcher *EKSFetcher) GetAccountId() (string, error) {
	accountId, _, _, err := stssession.GetMetaDetailsFromCreds("-service", false, "", os.Stderr)
	if err != nil {
		log.Fatalf("Unable to get account id from available credentials, error: %v", err)
	}
	return accountId, nil
}

// ensure interface is not broken in compile time
var _ Fetcher = &EKSFetcher{}
